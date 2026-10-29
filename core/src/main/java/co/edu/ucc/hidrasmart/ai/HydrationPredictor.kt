package co.edu.ucc.hidrasmart.ai

import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import kotlin.math.max
import kotlin.math.roundToInt

/** De dónde sale la recomendación de agua para un entrenamiento planeado. */
enum class PlanSource(val label: String) {
    PERSONAL_MODEL("Modelo IA personal"),
    SWEAT_TEST("Prueba de sudoración"),
    GENERAL_GUIDE("Guía general"),
}

/** Cuánta agua llevar a un entrenamiento. */
data class WaterPlan(
    val totalMl: Int,
    val perHourMl: Int,
    val source: PlanSource,
    val samples: Int,
    /** Qué tan bien explica el modelo los entrenamientos anteriores (0 a 1); null si no es el modelo. */
    val rSquared: Double?,
)

/** Modelo entrenado: tasa (ml/h) = a + b · intensidad. */
data class TrainedModel(val weights: DoubleArray, val samples: Int, val rSquared: Double) {
    /** ml por hora a una intensidad dada (fracción de la FC máxima). */
    fun ratePerHour(intensity: Double): Double = weights[0] + weights[1] * intensity

    override fun equals(other: Any?) = other is TrainedModel && weights.contentEquals(other.weights) &&
        samples == other.samples && rSquared == other.rSquared

    override fun hashCode() = weights.contentHashCode() * 31 + samples
}

/**
 * IA en el dispositivo: aprende de los entrenamientos guardados cuánta agua necesita
 * el atleta según la duración y la intensidad, y predice cuánta llevar al siguiente.
 *
 * Modelo físico simple: agua = horas × (a + b · intensidad), donde intensidad = FC media / FC máxima.
 * Se entrena con regresión ridge sobre las variables [horas, horas × intensidad] (sin intercepto:
 * 0 minutos de entrenamiento = 0 ml).
 *
 * Valor aprendido por sesión: los ml recomendados (tasa de sudoración × factor de condiciones);
 * si no hubo prueba de sudoración, lo que realmente tomó.
 */
object HydrationPredictor {

    const val MIN_SAMPLES = 3
    private const val MIN_DURATION_SEC = 10 * 60L
    private const val LAMBDA = 0.05
    /** Intensidad típica (70 % de la FC máxima) cuando no hay lecturas de frecuencia cardíaca. */
    const val DEFAULT_INTENSITY = 0.7
    /** Guía general cuando no hay datos: 400 a 800 ml/h; se usa el punto medio. */
    const val GENERAL_ML_PER_HOUR = 600
    private const val MAX_ML_PER_HOUR = 2500.0

    fun intensity(avgHeartRate: Int?, age: Int): Double {
        if (avgHeartRate == null || avgHeartRate <= 0) return DEFAULT_INTENSITY
        return (avgHeartRate.toDouble() / HydrationCalculator.maxHeartRate(age)).coerceIn(0.3, 1.1)
    }

    fun target(s: SavedSession): Double? = when {
        s.recommendedMl > 0 -> s.recommendedMl.toDouble()
        s.drankMl > 0 -> s.drankMl.toDouble()
        else -> null
    }

    fun features(durationMin: Double, intensity: Double): DoubleArray {
        val hours = durationMin / 60.0
        return doubleArrayOf(hours, hours * intensity)
    }

    /** Entrena con las sesiones válidas. Devuelve null si hay menos de [MIN_SAMPLES]. */
    fun train(sessions: List<SavedSession>, age: Int): TrainedModel? {
        val usable = sessions.filter { it.durationSec >= MIN_DURATION_SEC && target(it) != null }
        if (usable.size < MIN_SAMPLES) return null
        val x = usable.map { features(it.durationSec / 60.0, intensity(it.avgHeartRate, age)) }
        val y = usable.map { target(it)!! }
        val w = runCatching { RidgeRegression.fit(x, y, LAMBDA) }.getOrNull() ?: return null
        return TrainedModel(w, usable.size, RidgeRegression.rSquared(w, x, y).coerceIn(0.0, 1.0))
    }

    /**
     * Plan de agua para [durationMin] minutos de [type].
     * Usa las sesiones del mismo tipo si hay suficientes; si no, todas.
     */
    fun plan(type: String?, durationMin: Int, profile: Profile, sessions: List<SavedSession>): WaterPlan {
        require(durationMin > 0) { "La duración debe ser mayor que 0" }
        val sameType = sessions.filter { it.type == type }
        val pool = if (type != null && sameType.count { target(it) != null } >= MIN_SAMPLES) sameType else sessions
        val hours = durationMin / 60.0

        val model = train(pool, profile.age)
        if (model != null) {
            val heartRates = pool.mapNotNull { it.avgHeartRate }
            val expectedHr = if (heartRates.isEmpty()) null else heartRates.average().roundToInt()
            val perHour = model.ratePerHour(intensity(expectedHr, profile.age)).coerceIn(0.0, MAX_ML_PER_HOUR)
            if (perHour > 0) return build(perHour, hours, PlanSource.PERSONAL_MODEL, model.samples, model.rSquared)
        }
        val sweatRate = profile.sweatRateLh
        if (sweatRate != null) {
            return build(sweatRate * 1000.0, hours, PlanSource.SWEAT_TEST, 0, null)
        }
        return build(GENERAL_ML_PER_HOUR.toDouble(), hours, PlanSource.GENERAL_GUIDE, 0, null)
    }

    private fun build(perHour: Double, hours: Double, source: PlanSource, samples: Int, r2: Double?): WaterPlan {
        val total = max(0, ((perHour * hours) / 10.0).roundToInt() * 10)
        return WaterPlan(total, (perHour / 10.0).roundToInt() * 10, source, samples, r2)
    }
}
