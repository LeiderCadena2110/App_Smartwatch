package co.edu.ucc.hidrasmart.remote

import co.edu.ucc.hidrasmart.ai.CloudAnswer
import co.edu.ucc.hidrasmart.ai.CloudCoachException
import co.edu.ucc.hidrasmart.ai.CloudCoachException.Kind
import co.edu.ucc.hidrasmart.domain.Weather
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cliente del servidor hidrasmart-api (Google Cloud Run). La app no lleva claves: solo la URL
 * del servidor, el token de Firebase App Check y un id aleatorio de la instalación (para la cuota diaria).
 */
class HidraSmartApi(
    baseUrl: String,
    private val installId: String,
    private val appCheck: AppCheckTokenProvider?,
) {
    private val base = baseUrl.trim().trimEnd('/')

    val isConfigured: Boolean get() = base.isNotEmpty()

    /** POST /v1/coach. [body] lo arma CoachContextBuilder. Lanza [CloudCoachException]. */
    suspend fun coach(body: String): CloudAnswer {
        if (!isConfigured) throw CloudCoachException(Kind.NOT_CONFIGURED, "Sin servidor configurado")
        val (status, text) = send("POST", "/v1/coach", body, readTimeoutMs = COACH_READ_TIMEOUT_MS, withInstallId = true)
        if (status != 200) {
            throw CloudCoachException(kindFor(status), "El servidor respondió $status (${errorCode(text) ?: "sin código"})")
        }
        return parseCoach(text)
    }

    /** GET /v1/weather?city=. Lanza IOException si no hay clima. */
    suspend fun weather(city: String): Weather {
        if (!isConfigured) throw IOException("Sin servidor configurado")
        val path = "/v1/weather?city=" + URLEncoder.encode(city, "UTF-8")
        val (status, text) = try {
            send("GET", path, null, readTimeoutMs = WEATHER_READ_TIMEOUT_MS, withInstallId = false)
        } catch (e: CloudCoachException) {
            throw IOException(e.message, e)
        }
        if (status != 200) throw IOException("El servidor de clima respondió $status (${errorCode(text) ?: "sin código"})")
        return parseWeather(text)
    }

    private suspend fun send(
        method: String,
        path: String,
        body: String?,
        readTimeoutMs: Int,
        withInstallId: Boolean,
    ): Pair<Int, String> {
        // El token se pide antes de abrir la conexión; si App Check no está listo, el servidor responde 401
        val token = appCheck?.token()
        return withContext(Dispatchers.IO) {
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty(APP_CHECK_HEADER, token)
                if (withInstallId) setRequestProperty(INSTALL_ID_HEADER, installId)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                status to text
            } catch (e: UnknownHostException) {
                throw CloudCoachException(Kind.NO_INTERNET, "Sin internet", e)
            } catch (e: ConnectException) {
                throw CloudCoachException(Kind.NO_INTERNET, "No se pudo conectar con el servidor", e)
            } catch (e: SocketTimeoutException) {
                throw CloudCoachException(Kind.NO_INTERNET, "El servidor tardó demasiado", e)
            } catch (e: IOException) {
                throw CloudCoachException(Kind.UNAVAILABLE, "Error de red: ${e.message}", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val INSTALL_ID_HEADER = "X-Install-Id"
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** El servidor responde en máximo 8 s (reintento incluido); 2 s de margen para la red. */
        private const val COACH_READ_TIMEOUT_MS = 10_000
        private const val WEATHER_READ_TIMEOUT_MS = 6_000

        /** Código HTTP del servidor → tipo de error que ve el usuario. */
        fun kindFor(status: Int): Kind = when (status) {
            400, 413, 415 -> Kind.INVALID_REQUEST
            401 -> Kind.UNAUTHORIZED
            422 -> Kind.BLOCKED
            429 -> Kind.QUOTA_EXCEEDED
            else -> Kind.UNAVAILABLE
        }

        fun parseCoach(json: String): CloudAnswer {
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw CloudCoachException(Kind.UNAVAILABLE, "Respuesta del servidor no es JSON", e)
            }
            val text = o.optString("text").trim()
            if (text.isEmpty()) throw CloudCoachException(Kind.UNAVAILABLE, "Respuesta vacía del servidor")
            return CloudAnswer(
                text = text,
                model = o.optString("model"),
                promptVersion = o.optString("promptVersion"),
                cached = o.optBoolean("cached", false),
            )
        }

        fun parseWeather(json: String): Weather {
            val o = JSONObject(json)
            return Weather(
                temperatureC = o.getDouble("temperatureC"),
                humidityPct = o.getDouble("humidityPct"),
                city = o.getString("city"),
                timestamp = System.currentTimeMillis(),
            )
        }

        /** Campo "error" de las respuestas de error del servidor, si viene. */
        fun errorCode(json: String): String? =
            runCatching { JSONObject(json).optString("error").ifEmpty { null } }.getOrNull()
    }
}
