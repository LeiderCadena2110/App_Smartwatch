package co.edu.ucc.hidrasmart.data

import co.edu.ucc.hidrasmart.domain.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Consulta temperatura y humedad actuales en OpenWeatherMap (plan gratuito). */
object WeatherService {

    suspend fun fetch(city: String, apiKey: String): Weather = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(city, "UTF-8")
        val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$q&units=metric&appid=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val code = connection.responseCode
            if (code != 200) error("El servicio de clima respondió $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val main = JSONObject(text).getJSONObject("main")
            Weather(
                temperatureC = main.getDouble("temp"),
                humidityPct = main.getDouble("humidity"),
                city = city,
                timestamp = System.currentTimeMillis(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
