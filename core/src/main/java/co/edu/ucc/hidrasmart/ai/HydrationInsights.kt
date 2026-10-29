package co.edu.ucc.hidrasmart.ai

import co.edu.ucc.hidrasmart.domain.Intake
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

enum class RiskLevel { LOW, MEDIUM, HIGH }

/** Un hallazgo del coach: corto para que quepa en la pantalla del reloj. */
data class Insight(val title: String, val message: String, val level: RiskLevel)

/** Todo lo que el coach necesita saber del atleta en este momento. */
data class CoachInput(
    val profile: Profile,
    val intakes: List<Intake>,
    val sessions: List<SavedSession>,
    val now: LocalDateTime,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Factor de condiciones actual (1,0 a 1,3), si se conoce. */
    val conditionsFactor: Double? = null,
    /** Hay un entrenamiento en curso. */
    val inWorkout: Boolean = false,
)

/**
 * Sistema experto (IA basada en reglas) que analiza los datos guardados y produce
 * consejos priorizados. Funciona sin internet y es la base del coach generativo:
 * sus resultados se envían como contexto a Gemini.
 */
object HydrationInsights {

    /** Ventana del día en la que se reparte la meta (7:00 a 21:00). */
    val DAY_START: LocalTime = LocalTime.of(7, 0)
    val DAY_END: LocalTime = LocalTime.of(21, 0)

    fun dayOf(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun totalOn(day: LocalDate, intakes: List<Intake>, zone: ZoneId): Int =
        intakes.filter { dayOf(it.timestamp, zone) == day }.sumOf { it.ml }

    /** Lo que debería llevar tomado a esta hora si reparte la meta de forma pareja. */
    fun expectedByNow(goalMl: Int, time: LocalTime): Int {
        if (time <= DAY_START) return 0
        if (time >= DAY_END) return goalMl
        val total = (DAY_END.toSecondOfDay() - DAY_START.toSecondOfDay()).toDouble()
        val elapsed = (time.toSecondOfDay() - DAY_START.toSecondOfDay()).toDouble()
        return (goalMl * elapsed / total).roundToInt()
    }

    /** Proporción tomada / recomendada en los últimos [last] entrenamientos con recomendación. */
    fun adherence(sessions: List<SavedSession>, last: Int = 5): Double? {
        val recent = sessions.filter { it.recommendedMl > 0 }.sortedByDescending { it.startTime }.take(last)
        if (recent.isEmpty()) return null
        return recent.sumOf { it.drankMl }.toDouble() / recent.sumOf { it.recommendedMl }
    }

    /** Días seguidos cumpliendo la meta. Hoy cuenta solo si ya se cumplió. */
    fun goalStreak(intakes: List<Intake>, goalMl: Int, today: LocalDate, zone: ZoneId): Int {
        if (goalMl <= 0) return 0
        val totals = intakes.groupBy { dayOf(it.timestamp, zone) }.mapValues { (_, v) -> v.sumOf { it.ml } }
        var day = if ((totals[today] ?: 0) >= goalMl) today else today.minusDays(1)
        var streak = 0
        while ((totals[day] ?: 0) >= goalMl) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Promedio diario de los últimos 7 días (sin contar hoy). */
    fun weeklyAverage(intakes: List<Intake>, today: LocalDate, zone: ZoneId): Int {
        val days = (1L..7L).map { today.minusDays(it) }
        return (days.sumOf { totalOn(it, intakes, zone) } / 7.0).roundToInt()
    }

    /** Hallazgos ordenados del más importante al menos importante. */
    fun analyze(input: CoachInput): List<Insight> {
        val today = input.now.toLocalDate()
        val goal = input.profile.dailyGoalMl
        val todayMl = totalOn(today, input.intakes, input.zone)
        val expected = expectedByNow(goal, input.now.toLocalTime())
        val behind = expected - todayMl
        val out = mutableListOf<Insight>()

        when {
            todayMl >= goal -> out += Insight("Meta cumplida", "Llevas $todayMl ml: cumpliste tu meta de hoy.", RiskLevel.LOW)
            behind >= 500 -> out += Insight(
                "Vas atrasado",
                "Llevas $todayMl ml y a esta hora deberías ir en $expected ml. Toma ${roundTo50(behind)} ml pronto.",
                RiskLevel.HIGH,
            )
            behind >= 200 -> out += Insight(
                "Un poco atrás",
                "Te faltan ${roundTo50(behind)} ml para ir al día. Un vaso ahora te pone al ritmo.",
                RiskLevel.MEDIUM,
            )
            else -> out += Insight("Buen ritmo", "Llevas $todayMl de $goal ml. Sigue así.", RiskLevel.LOW)
        }

        val factor = input.conditionsFactor
        if (factor != null && factor >= 1.2) {
            out += Insight(
                "Condiciones exigentes",
                "Hoy sudas más de lo normal (×${"%.1f".format(factor).replace('.', ',')}). Toma antes de tener sed.",
                RiskLevel.MEDIUM,
            )
        }

        val adherence = adherence(input.sessions)
        if (adherence != null) {
            val pct = (adherence * 100).roundToInt()
            out += when {
                adherence < 0.6 -> Insight(
                    "Tomas poco al entrenar",
                    "En tus últimos entrenos tomaste el $pct % de lo recomendado. Responde las alertas con \"Ya tomé\".",
                    RiskLevel.HIGH,
                )
                adherence < 0.85 -> Insight(
                    "Casi al día en entrenos",
                    "Tomaste el $pct % de lo recomendado al entrenar. Intenta llegar al 90 %.",
                    RiskLevel.MEDIUM,
                )
                else -> Insight("Buena hidratación al entrenar", "Tomaste el $pct % de lo recomendado. ¡Muy bien!", RiskLevel.LOW)
            }
        }

        val streak = goalStreak(input.intakes, goal, today, input.zone)
        if (streak >= 2) out += Insight("Racha de $streak días", "Llevas $streak días seguidos cumpliendo tu meta.", RiskLevel.LOW)

        if (input.profile.sweatRateLh == null) {
            out += Insight(
                "Personaliza tus alertas",
                "Haz la prueba de sudoración en Perfil para recibir mililitros exactos.",
                RiskLevel.MEDIUM,
            )
        }
        return out.sortedByDescending { it.level.ordinal }
    }

    /** Nivel de riesgo general: el peor de los hallazgos. */
    fun overallRisk(insights: List<Insight>): RiskLevel = insights.maxOfOrNull { it.level } ?: RiskLevel.LOW

    private fun roundTo50(ml: Int): Int = ((ml + 25) / 50) * 50
}
