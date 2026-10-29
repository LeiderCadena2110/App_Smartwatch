package co.edu.ucc.hidrasmart.ai

import java.time.format.DateTimeFormatter

/**
 * Arma el contexto y la petición para el coach generativo (Gemini).
 * Solo envía datos de hidratación y entrenamiento: nada que identifique a la persona.
 */
object PromptBuilder {

    fun systemInstruction(compact: Boolean): String = buildString {
        append("Eres HidraCoach, el asistente de hidratación deportiva de la app HidraSmart. ")
        append("Responde siempre en español de Colombia, en tono cercano y motivador. ")
        append("Usa solo los datos que te doy; si falta un dato, dilo. ")
        append("No hagas diagnósticos médicos: ante síntomas como mareo, confusión o calambres fuertes, ")
        append("recomienda parar y buscar atención médica. ")
        if (compact) append("La respuesta se lee en un reloj: máximo 2 oraciones y 200 caracteres, sin listas ni emojis.")
        else append("Responde en máximo 5 oraciones. Puedes usar una lista corta si ayuda.")
    }

    fun context(input: CoachInput, plan: WaterPlan?): String {
        val p = input.profile
        val today = input.now.toLocalDate()
        val todayMl = HydrationInsights.totalOn(today, input.intakes, input.zone)
        val insights = HydrationInsights.analyze(input)
        val adherence = HydrationInsights.adherence(input.sessions)
        val recent = input.sessions.sortedByDescending { it.startTime }.take(5)
        val hour = input.now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

        return buildString {
            appendLine("DATOS DEL ATLETA")
            appendLine("- Hora actual: $hour")
            appendLine("- Edad: ${p.age} años, peso: ${p.weightKg} kg")
            appendLine("- Meta diaria: ${p.dailyGoalMl} ml; tomado hoy: $todayMl ml " +
                "(esperado a esta hora: ${HydrationInsights.expectedByNow(p.dailyGoalMl, input.now.toLocalTime())} ml)")
            appendLine("- Promedio diario últimos 7 días: ${HydrationInsights.weeklyAverage(input.intakes, today, input.zone)} ml")
            appendLine("- Racha cumpliendo la meta: ${HydrationInsights.goalStreak(input.intakes, p.dailyGoalMl, today, input.zone)} días")
            appendLine("- Tasa de sudoración: ${p.sweatRateLh?.let { "%.2f L/h".format(it) } ?: "sin prueba de sudoración"}")
            input.conditionsFactor?.let { appendLine("- Factor de condiciones hoy (altitud, calor, humedad, FC): ×${"%.1f".format(it)}") }
            appendLine("- Entrenando ahora: ${if (input.inWorkout) "sí" else "no"}")
            adherence?.let { appendLine("- Cumplimiento en entrenos (tomado/recomendado): ${(it * 100).toInt()} %") }
            if (recent.isNotEmpty()) {
                appendLine("ÚLTIMOS ENTRENAMIENTOS")
                recent.forEach { s ->
                    appendLine("- ${s.type}: ${s.durationSec / 60} min, FC media ${s.avgHeartRate ?: "?"} lpm, " +
                        "tomó ${s.drankMl} ml de ${s.recommendedMl} ml recomendados")
                }
            }
            plan?.let {
                appendLine("PREDICCIÓN DEL MODELO EN EL DISPOSITIVO")
                appendLine("- Para 1 hora: ${it.totalMl} ml (${it.perHourMl} ml/h), fuente: ${it.source.label}")
            }
            appendLine("ANÁLISIS AUTOMÁTICO")
            insights.forEach { appendLine("- [${it.level}] ${it.title}: ${it.message}") }
        }
    }

    fun userPrompt(question: String?, context: String): String = buildString {
        append(context)
        appendLine()
        if (question.isNullOrBlank()) append("Dame el consejo más útil para este momento.")
        else append("PREGUNTA: ").append(question.trim())
    }

    /** Cuerpo JSON para generateContent de la API de Gemini (sin librerías, para poder probarlo en la JVM). */
    fun requestJson(system: String, prompt: String, maxOutputTokens: Int, temperature: Double): String =
        """{"systemInstruction":{"parts":[{"text":"${escape(system)}"}]},""" +
            """"contents":[{"role":"user","parts":[{"text":"${escape(prompt)}"}]}],""" +
            """"generationConfig":{"maxOutputTokens":$maxOutputTokens,"temperature":$temperature}}"""

    /** Escapa texto para meterlo dentro de una cadena JSON. */
    fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    /** Recorta la respuesta para la pantalla del reloj sin cortar palabras. */
    fun fitForWatch(text: String, maxChars: Int = 240): String {
        val clean = text.replace(Regex("[*#_`]"), "").replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf(' '))
        return (if (end > maxChars / 2) cut.take(end + 1) else cut).trimEnd() + "…"
    }
}
