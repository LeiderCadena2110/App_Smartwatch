package co.edu.ucc.hidrasmart.ai

import kotlinx.coroutines.CancellationException

/** Respuesta del coach. [fromCloud] = la generó Gemini; si es false, la dio el coach local. */
data class CoachAnswer(val text: String, val fromCloud: Boolean, val note: String? = null)

/**
 * Coach de hidratación con IA. Intenta con Gemini (IA generativa en la nube) usando como
 * contexto el análisis local y la predicción del modelo en el dispositivo. Si no hay clave,
 * no hay internet o la API falla, responde el coach local para que la app nunca se quede sin respuesta.
 */
class AiCoach(private val client: GeminiClient?) {

    val cloudEnabled: Boolean get() = client?.isConfigured == true

    suspend fun ask(question: String?, input: CoachInput, compact: Boolean): CoachAnswer {
        val plan = HydrationPredictor.plan(null, 60, input.profile, input.sessions)
        val gemini = client
        if (gemini != null && gemini.isConfigured) {
            try {
                val prompt = PromptBuilder.userPrompt(question, PromptBuilder.context(input, plan))
                val text = gemini.generate(PromptBuilder.systemInstruction(compact), prompt)
                return CoachAnswer(if (compact) PromptBuilder.fitForWatch(text) else text.trim(), fromCloud = true)
            } catch (e: CancellationException) {
                throw e // la pantalla se cerró: no es un error de red
            } catch (e: Exception) {
                val local = LocalCoach.answer(question, input, plan)
                return CoachAnswer(local, fromCloud = false, note = "Sin conexión con Gemini: respuesta local")
            }
        }
        return CoachAnswer(LocalCoach.answer(question, input, plan), fromCloud = false)
    }
}
