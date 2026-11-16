package co.edu.ucc.hidrasmart.ai

import kotlinx.coroutines.CancellationException

/** Respuesta del coach. [fromCloud] = la generó Gemini a través del servidor; si es false, la dio el coach local. */
data class CoachAnswer(val text: String, val fromCloud: Boolean, val note: String? = null)

/**
 * Coach de hidratación con IA. Pregunta al coach en la nube (servidor hidrasmart-api → Gemini)
 * enviando como contexto el análisis local y la predicción del modelo en el dispositivo.
 * Si no hay servidor, no hay internet, se acabó la cuota o la nube falla, responde el coach local:
 * la app nunca se queda sin respuesta, y la nota dice por qué.
 */
class AiCoach(private val cloud: CloudCoach?) {

    val cloudEnabled: Boolean get() = cloud?.isConfigured == true

    suspend fun ask(question: String?, input: CoachInput, compact: Boolean): CoachAnswer {
        val plan = HydrationPredictor.plan(null, 60, input.profile, input.sessions)
        val remote = cloud
        if (remote != null && remote.isConfigured) {
            try {
                val answer = remote.ask(question, input, plan, compact)
                val text = if (compact) CoachText.fitForWatch(answer.text) else answer.text.trim()
                return CoachAnswer(text, fromCloud = true)
            } catch (e: CancellationException) {
                throw e // la pantalla se cerró: no es un error de red
            } catch (e: CloudCoachException) {
                return CoachAnswer(LocalCoach.answer(question, input, plan), fromCloud = false, note = noteFor(e.kind))
            } catch (e: Exception) {
                return CoachAnswer(LocalCoach.answer(question, input, plan), fromCloud = false, note = noteFor(CloudCoachException.Kind.UNAVAILABLE))
            }
        }
        return CoachAnswer(LocalCoach.answer(question, input, plan), fromCloud = false)
    }

    companion object {
        /** Texto corto que la pantalla muestra bajo la respuesta local. */
        fun noteFor(kind: CloudCoachException.Kind): String? = when (kind) {
            CloudCoachException.Kind.NOT_CONFIGURED -> null
            CloudCoachException.Kind.NO_INTERNET -> "Sin internet: respuesta local"
            CloudCoachException.Kind.QUOTA_EXCEEDED -> "Límite diario del coach en la nube: respuesta local"
            CloudCoachException.Kind.BLOCKED -> "Solo respondo sobre hidratación y ejercicio: respuesta local"
            CloudCoachException.Kind.UNAUTHORIZED -> "No se pudo verificar la app: respuesta local"
            CloudCoachException.Kind.INVALID_REQUEST -> "Error al preparar la pregunta: respuesta local"
            CloudCoachException.Kind.UNAVAILABLE -> "Coach en la nube no disponible: respuesta local"
        }
    }
}
