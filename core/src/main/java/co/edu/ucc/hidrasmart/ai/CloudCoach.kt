package co.edu.ucc.hidrasmart.ai

/** Respuesta del coach en la nube (servidor hidrasmart-api, que usa Gemini). */
data class CloudAnswer(
    val text: String,
    val model: String,
    val promptVersion: String,
    val cached: Boolean,
)

/** Por qué falló el coach en la nube. Cada caso se muestra distinto y siempre responde la IA local. */
class CloudCoachException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind {
        /** No hay servidor configurado (API_BASE_URL vacía). */
        NOT_CONFIGURED,

        /** Sin red o el servidor no responde a tiempo. */
        NO_INTERNET,

        /** El servidor no reconoció la app (App Check). */
        UNAUTHORIZED,

        /** Se acabaron las preguntas del día. */
        QUOTA_EXCEEDED,

        /** La pregunta o la respuesta se bloqueó por seguridad. */
        BLOCKED,

        /** La petición no cumple el contrato del API (error de la app). */
        INVALID_REQUEST,

        /** El servidor o Gemini no están disponibles. */
        UNAVAILABLE,
    }
}

/** Coach generativo en la nube. La app no habla con Gemini directamente: lo hace el servidor. */
interface CloudCoach {
    val isConfigured: Boolean

    /** Lanza [CloudCoachException] si no puede responder. */
    suspend fun ask(question: String?, input: CoachInput, plan: WaterPlan?, compact: Boolean): CloudAnswer
}
