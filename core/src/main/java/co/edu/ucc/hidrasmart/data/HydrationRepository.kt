package co.edu.ucc.hidrasmart.data

import android.content.Context
import android.content.SharedPreferences
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.domain.Weather
import co.edu.ucc.hidrasmart.domain.Conditions
import co.edu.ucc.hidrasmart.domain.Factor
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.ActiveSession
import co.edu.ucc.hidrasmart.domain.SavedSession
import co.edu.ucc.hidrasmart.domain.Intake
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda todo en el reloj (SharedPreferences con JSON). La app funciona sin internet
 * y sin teléfono. Para muchos datos se podría migrar a Room más adelante.
 */
class HydrationRepository(context: Context) {

    private val prefs = context.getSharedPreferences("hidrasmart", Context.MODE_PRIVATE)

    // ---------- Perfil ----------
    fun isProfileConfigured(): Boolean = prefs.contains(PROFILE)

    fun profile(): Profile {
        val text = prefs.getString(PROFILE, null) ?: return Profile()
        val o = JSONObject(text)
        return Profile(
            weightKg = o.optDouble("pesoKg", 70.0),
            age = o.optInt("edad", 22),
            dailyGoalMl = o.optInt("metaDiariaMl", 2000),
            intervalMin = o.optInt("intervaloMin", 15),
            city = o.optString("ciudad", "Pasto,CO"),
            usualAltitudeM = o.optDouble("altitudHabitualM", 2527.0),
            manualTemperatureC = o.optDouble("temperaturaManualC", 14.0),
            manualHumidityPct = o.optDouble("humedadManualPct", 75.0),
            sweatRateLh = o.doubleOrNull("tasaSudoracionLh"),
            testConditions = o.optJSONObject("condicionesPrueba")?.toConditions(),
        )
    }

    fun saveProfile(p: Profile) {
        val o = JSONObject()
            .put("pesoKg", p.weightKg)
            .put("edad", p.age)
            .put("metaDiariaMl", p.dailyGoalMl)
            .put("intervaloMin", p.intervalMin)
            .put("ciudad", p.city)
            .put("altitudHabitualM", p.usualAltitudeM)
            .put("temperaturaManualC", p.manualTemperatureC)
            .put("humedadManualPct", p.manualHumidityPct)
        p.sweatRateLh?.let { o.put("tasaSudoracionLh", it) }
        p.testConditions?.let { o.put("condicionesPrueba", it.toJson()) }
        prefs.edit().putString(PROFILE, o.toString()).apply()
    }

    // ---------- Tomas de agua ----------
    fun intakes(): List<Intake> = readList(INTAKES) { Intake(it.getLong("instante"), it.getInt("ml")) }

    fun addIntake(t: Intake) {
        val items = intakes() + t
        writeList(INTAKES, items.takeLast(MAX_INTAKES)) { JSONObject().put("instante", it.timestamp).put("ml", it.ml) }
    }

    // ---------- Sesiones de entrenamiento ----------
    fun sessions(): List<SavedSession> = readList(SESSIONS) {
        SavedSession(
            id = it.getLong("id"),
            type = it.getString("tipo"),
            startTime = it.getLong("inicio"),
            endTime = it.getLong("fin"),
            durationSec = it.getLong("duracionSeg"),
            avgHeartRate = if (it.has("fcMedia")) it.getInt("fcMedia") else null,
            drankMl = it.getInt("tomadoMl"),
            recommendedMl = it.getInt("recomendadoMl"),
        )
    }

    fun saveSession(s: SavedSession) {
        val items = sessions() + s
        writeList(SESSIONS, items.takeLast(MAX_SESSIONS)) {
            val o = JSONObject()
                .put("id", it.id).put("tipo", it.type).put("inicio", it.startTime).put("fin", it.endTime)
                .put("duracionSeg", it.durationSec).put("tomadoMl", it.drankMl).put("recomendadoMl", it.recommendedMl)
            it.avgHeartRate?.let { heartRate -> o.put("fcMedia", heartRate) }
            o
        }
    }

    // ---------- Clima (último valor, para usarlo sin conexión) ----------
    fun lastWeather(): Weather? {
        val text = prefs.getString(WEATHER, null) ?: return null
        val o = JSONObject(text)
        return Weather(o.getDouble("t"), o.getDouble("h"), o.getString("ciudad"), o.getLong("instante"))
    }

    fun saveWeather(c: Weather) {
        val o = JSONObject().put("t", c.temperatureC).put("h", c.humidityPct).put("ciudad", c.city).put("instante", c.timestamp)
        prefs.edit().putString(WEATHER, o.toString()).apply()
    }

    // ---------- Sesión activa (sobrevive aunque Android cierre la app) ----------
    fun activeSession(): ActiveSession? {
        val text = prefs.getString(ACTIVE_SESSION, null) ?: return null
        val o = JSONObject(text)
        return ActiveSession(
            type = o.getString("tipo"),
            startTime = o.getLong("inicio"),
            nextAlertAt = o.getLong("proximaAlerta"),
            pausedSince = if (o.has("pausadaDesde")) o.getLong("pausadaDesde") else null,
            pausedMs = o.optLong("pausadoMs", 0),
            drankMl = o.optInt("tomadoMl", 0),
            recommendedMl = o.optInt("recomendadoMl", 0),
            heartRateSum = o.optLong("fcSuma", 0),
            heartRateReadings = o.optInt("fcLecturas", 0),
        )
    }

    fun saveActiveSession(s: ActiveSession?) {
        if (s == null) {
            prefs.edit().remove(ACTIVE_SESSION).apply()
            return
        }
        val o = JSONObject()
            .put("tipo", s.type).put("inicio", s.startTime).put("proximaAlerta", s.nextAlertAt)
            .put("pausadoMs", s.pausedMs).put("tomadoMl", s.drankMl).put("recomendadoMl", s.recommendedMl)
            .put("fcSuma", s.heartRateSum).put("fcLecturas", s.heartRateReadings)
        s.pausedSince?.let { o.put("pausadaDesde", it) }
        prefs.edit().putString(ACTIVE_SESSION, o.toString()).apply()
    }

    // ---------- Alerta pendiente ----------
    fun pendingAlert(): IntakeAlert? {
        val text = prefs.getString(ALERT, null) ?: return null
        val o = JSONObject(text)
        val reasons = o.optJSONArray("razones")
        return IntakeAlert(
            ml = if (o.has("ml")) o.getInt("ml") else null,
            factor = Factor(
                value = o.optDouble("factor", 1.0),
                reasons = if (reasons == null) emptyList() else (0 until reasons.length()).map { reasons.getString(it) },
            ),
            timestamp = o.optLong("instante", 0),
        )
    }

    fun saveAlert(a: IntakeAlert?) {
        if (a == null) {
            prefs.edit().remove(ALERT).apply()
            return
        }
        val reasons = JSONArray()
        a.factor.reasons.forEach { reasons.put(it) }
        val o = JSONObject().put("factor", a.factor.value).put("razones", reasons).put("instante", a.timestamp)
        a.ml?.let { o.put("ml", it) }
        prefs.edit().putString(ALERT, o.toString()).apply()
    }

    // ---------- Últimas lecturas de sensores (para calcular la alerta con la app cerrada) ----------
    fun lastHeartRate(): Int? = if (prefs.contains(LAST_HEART_RATE)) prefs.getInt(LAST_HEART_RATE, 0) else null

    fun lastAltitude(): Double? =
        if (prefs.contains(LAST_ALTITUDE)) prefs.getFloat(LAST_ALTITUDE, 0f).toDouble() else null

    fun saveReadings(heartRate: Int?, altitude: Double?) {
        val e = prefs.edit()
        if (heartRate != null) e.putInt(LAST_HEART_RATE, heartRate)
        if (altitude != null) e.putFloat(LAST_ALTITUDE, altitude.toFloat())
        e.apply()
    }

    // ---------- Avisos de cambios (la app se actualiza cuando la alarma cambia algo) ----------
    fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun stopListening(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    // ---------- Utilidades ----------
    private fun <T> readList(key: String, convert: (JSONObject) -> T): List<T> {
        val text = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).map { convert(array.getJSONObject(it)) }
    }

    private fun <T> writeList(key: String, items: List<T>, convert: (T) -> JSONObject) {
        val array = JSONArray()
        items.forEach { array.put(convert(it)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private fun JSONObject.toConditions() = Conditions(
        altitudeM = doubleOrNull("altitudM"),
        temperatureC = doubleOrNull("temperaturaC"),
        humidityPct = doubleOrNull("humedadPct"),
    )

    private fun Conditions.toJson(): JSONObject {
        val o = JSONObject()
        altitudeM?.let { o.put("altitudM", it) }
        temperatureC?.let { o.put("temperaturaC", it) }
        humidityPct?.let { o.put("humedadPct", it) }
        return o
    }

    companion object {
        /** Claves que cambian con cada lectura de sensor: la app no necesita recargarse por ellas. */
        val READING_KEYS = setOf("ultima_fc", "ultima_altitud")

        const val PROFILE = "perfil"
        const val INTAKES = "tomas"
        const val SESSIONS = "sesiones"
        const val WEATHER = "clima"
        const val ACTIVE_SESSION = "sesion_activa"
        const val ALERT = "alerta"
        const val LAST_HEART_RATE = "ultima_fc"
        const val LAST_ALTITUDE = "ultima_altitud"
        const val MAX_INTAKES = 2000
        const val MAX_SESSIONS = 300
    }
}
