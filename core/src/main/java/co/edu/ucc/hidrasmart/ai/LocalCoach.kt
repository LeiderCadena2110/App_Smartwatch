package co.edu.ucc.hidrasmart.ai

import java.text.Normalizer

/**
 * Coach sin conexión: responde preguntas frecuentes con los datos del atleta.
 * Se usa cuando no hay clave de Gemini, no hay internet o la nube falla.
 */
object LocalCoach {

    /** Preguntas sugeridas (botones rápidos en el reloj y en el celular). */
    val SUGGESTED_QUESTIONS = listOf(
        "¿Cómo voy hoy?",
        "¿Cuánta agua llevo para entrenar 1 hora?",
        "Dame un consejo para hoy",
    )

    fun answer(question: String?, input: CoachInput, plan: WaterPlan?): String {
        val insights = HydrationInsights.analyze(input)
        val q = normalize(question ?: "")
        val today = HydrationInsights.totalOn(input.now.toLocalDate(), input.intakes, input.zone)
        val goal = input.profile.dailyGoalMl

        return when {
            q.isBlank() || q.contains("consejo") || q.contains("recomienda") ->
                insights.firstOrNull()?.message ?: "Toma agua de forma regular durante el día."

            listOf("llevo", "lleve", "llevar", "botella", "entren", "planea").any { q.contains(it) } && plan != null ->
                "Lleva unos ${plan.totalMl} ml (${plan.perHourMl} ml por hora). Fuente: ${plan.source.label}."

            listOf("como voy", "hoy", "meta", "cuanto he", "cuanto tomo").any { q.contains(it) } -> {
                val faltan = (goal - today).coerceAtLeast(0)
                if (faltan == 0) "Llevas $today ml: ya cumpliste tu meta de $goal ml."
                else "Llevas $today de $goal ml. Te faltan $faltan ml. ${insights.first().message}"
            }

            q.contains("sed") || q.contains("deshidrat") || q.contains("mare") || q.contains("calambre") ->
                "Si tienes mareo, calambres o mucha sed, para, toma agua y descansa. " +
                    "Si no mejoras, busca atención médica."

            q.contains("sudor") -> "La prueba de sudoración está en Perfil: pésate antes y después de 1 hora de ejercicio."

            else -> insights.firstOrNull()?.message ?: "Toma agua de forma regular durante el día."
        }
    }

    /** Minúsculas y sin tildes, para comparar palabras clave. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace("¿", "").replace("?", "").trim()
}
