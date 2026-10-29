package co.edu.ucc.hidrasmart.data

import co.edu.ucc.hidrasmart.domain.Clima
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Consulta temperatura y humedad actuales en OpenWeatherMap (plan gratuito). */
object ClimaServicio {

    suspend fun consultar(ciudad: String, apiKey: String): Clima = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(ciudad, "UTF-8")
        val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$q&units=metric&appid=$apiKey")
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val codigo = conexion.responseCode
            if (codigo != 200) error("El servicio de clima respondió $codigo")
            val texto = conexion.inputStream.bufferedReader().use { it.readText() }
            val main = JSONObject(texto).getJSONObject("main")
            Clima(
                temperaturaC = main.getDouble("temp"),
                humedadPct = main.getDouble("humidity"),
                ciudad = ciudad,
                instante = System.currentTimeMillis(),
            )
        } finally {
            conexion.disconnect()
        }
    }
}
