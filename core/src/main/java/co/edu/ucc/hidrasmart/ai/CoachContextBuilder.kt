package co.edu.ucc.hidrasmart.ai

import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arma el cuerpo de POST /v1/coach con datos agregados del atleta (no el historial crudo).
 * El servidor arma el prompt con estos datos; ver server/src/schemas.ts para el contrato.
 *
 * Cada valor se recorta al rango que acepta el servidor: un dato raro (por ejemplo, una toma
 * enorme registrada por error) no debe hacer fallar la pregunta con un 400.
 */
object CoachContextBuilder {

    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun requestBody(question: String?, compact: Boolean, input: CoachInput, plan: WaterPlan?): String {
        val body = JSONObject()
            .put("question", question?.trim()?.take(MAX_QUESTION)?.ifEmpty { null } ?: JSONObject.NULL)
            .put("compact", compact)
            .put("context", context(input, plan))
        return body.toString()
    }

    fun context(input: CoachInput, plan: WaterPlan?): JSONObject {
        val p = input.profile
        val today = input.now.toLocalDate()
        val recent = input.sessions.sortedByDescending { it.startTime }.take(5)

        return JSONObject()
            .put("localTime", input.now.toLocalTime().format(timeFormat))
            .put("age", p.age.coerceIn(10, 100))
            .put("weightKg", p.weightKg.coerceIn(30.0, 250.0))
            .put("dailyGoalMl", p.dailyGoalMl.coerceIn(500, 8000))
            .put("todayMl", HydrationInsights.totalOn(today, input.intakes, input.zone).coerceIn(0, 20_000))
            .put("expectedByNowMl", HydrationInsights.expectedByNow(p.dailyGoalMl, input.now.toLocalTime()).coerceIn(0, 20_000))
            .put("weeklyAverageMl", HydrationInsights.weeklyAverage(input.intakes, today, input.zone).coerceIn(0, 20_000))
            .put("goalStreakDays", HydrationInsights.goalStreak(input.intakes, p.dailyGoalMl, today, input.zone).coerceIn(0, 3650))
            .put("sweatRateLh", p.sweatRateLh?.coerceIn(0.05, 4.0) ?: JSONObject.NULL)
            .put("conditionsFactor", input.conditionsFactor?.coerceIn(0.5, 2.0) ?: JSONObject.NULL)
            .put("inWorkout", input.inWorkout)
            .put("adherencePct", HydrationInsights.adherence(input.sessions)?.let { (it * 100).roundToInt().coerceIn(0, 1000) } ?: JSONObject.NULL)
            .put(
                "recentSessions",
                JSONArray().apply {
                    recent.forEach { s ->
                        put(
                            JSONObject()
                                .put("type", s.type.trim().ifEmpty { "Entrenamiento" }.take(30))
                                .put("minutes", (s.durationSec / 60).toInt().coerceIn(0, 600))
                                .put("avgHeartRate", s.avgHeartRate?.coerceIn(30, 230) ?: JSONObject.NULL)
                                .put("drankMl", s.drankMl.coerceIn(0, 10_000))
                                .put("recommendedMl", s.recommendedMl.coerceIn(0, 10_000)),
                        )
                    }
                },
            )
            .put(
                "plan",
                plan?.let {
                    JSONObject()
                        .put("totalMl", it.totalMl.coerceIn(0, 10_000))
                        .put("perHourMl", it.perHourMl.coerceIn(0, 5000))
                        .put("source", it.source.label.take(60))
                } ?: JSONObject.NULL,
            )
            .put(
                "insights",
                JSONArray().apply {
                    HydrationInsights.analyze(input).take(6).forEach { insight ->
                        put(
                            JSONObject()
                                .put("level", insight.level.name)
                                .put("title", insight.title.take(80))
                                .put("message", insight.message.take(240)),
                        )
                    }
                },
            )
    }

    /** Mismo límite que el servidor. */
    const val MAX_QUESTION = 300
}
