package co.edu.ucc.hidrasmart.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo de la API de Gemini (Google AI Studio) con HttpURLConnection.
 * La clave se lee de local.properties (GEMINI_API_KEY) y nunca se sube a GitHub.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Devuelve el texto generado. Lanza IOException si no hay conexión o la API responde con error. */
    suspend fun generate(system: String, prompt: String, maxOutputTokens: Int = 512, temperature: Double = 0.6): String =
        withContext(Dispatchers.IO) {
            check(isConfigured) { "Falta GEMINI_API_KEY" }
            val url = URL("$BASE_URL/${model.ifBlank { DEFAULT_MODEL }}:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                val body = PromptBuilder.requestJson(system, prompt, maxOutputTokens, temperature)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("Gemini respondió $code: ${errorMessage(text)}")
                parse(text)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        /** Modelo rápido y económico; se puede cambiar con GEMINI_MODEL en local.properties. */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /** Une los textos de la primera respuesta candidata. */
        fun parse(json: String): String {
            val root = JSONObject(json)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw IOException(if (reason.isNotBlank()) "Respuesta bloqueada ($reason)" else "Respuesta vacía")
            }
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw IOException("Respuesta sin contenido")
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    // Algunos modelos devuelven partes de "pensamiento"; no se muestran
                    if (!part.optBoolean("thought", false)) append(part.optString("text"))
                }
            }.trim()
            if (text.isEmpty()) throw IOException("Respuesta vacía")
            return text
        }

        private fun errorMessage(json: String): String =
            runCatching { JSONObject(json).getJSONObject("error").getString("message") }.getOrDefault(json.take(160))
    }
}
