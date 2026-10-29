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
class Repositorio(contexto: Context) {

    private val prefs = contexto.getSharedPreferences("hidrasmart", Context.MODE_PRIVATE)

    // ---------- Perfil ----------
    fun perfilConfigurado(): Boolean = prefs.contains(PERFIL)

    fun perfil(): Profile {
        val texto = prefs.getString(PERFIL, null) ?: return Profile()
        val o = JSONObject(texto)
        return Profile(
            weightKg = o.optDouble("pesoKg", 70.0),
            age = o.optInt("edad", 22),
            dailyGoalMl = o.optInt("metaDiariaMl", 2000),
            intervalMin = o.optInt("intervaloMin", 15),
            city = o.optString("ciudad", "Pasto,CO"),
            usualAltitudeM = o.optDouble("altitudHabitualM", 2527.0),
            manualTemperatureC = o.optDouble("temperaturaManualC", 14.0),
            manualHumidityPct = o.optDouble("humedadManualPct", 75.0),
            sweatRateLh = o.dobleONulo("tasaSudoracionLh"),
            testConditions = o.optJSONObject("condicionesPrueba")?.aCondiciones(),
        )
    }

    fun guardarPerfil(p: Profile) {
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
        p.testConditions?.let { o.put("condicionesPrueba", it.aJson()) }
        prefs.edit().putString(PERFIL, o.toString()).apply()
    }

    // ---------- Tomas de agua ----------
    fun tomas(): List<Intake> = leerLista(TOMAS) { Intake(it.getLong("instante"), it.getInt("ml")) }

    fun agregarToma(t: Intake) {
        val lista = tomas() + t
        guardarLista(TOMAS, lista.takeLast(MAX_TOMAS)) { JSONObject().put("instante", it.timestamp).put("ml", it.ml) }
    }

    // ---------- Sesiones de entrenamiento ----------
    fun sesiones(): List<SavedSession> = leerLista(SESIONES) {
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

    fun guardarSesion(s: SavedSession) {
        val lista = sesiones() + s
        guardarLista(SESIONES, lista.takeLast(MAX_SESIONES)) {
            val o = JSONObject()
                .put("id", it.id).put("tipo", it.type).put("inicio", it.startTime).put("fin", it.endTime)
                .put("duracionSeg", it.durationSec).put("tomadoMl", it.drankMl).put("recomendadoMl", it.recommendedMl)
            it.avgHeartRate?.let { fc -> o.put("fcMedia", fc) }
            o
        }
    }

    // ---------- Clima (último valor, para usarlo sin conexión) ----------
    fun ultimoClima(): Weather? {
        val texto = prefs.getString(CLIMA, null) ?: return null
        val o = JSONObject(texto)
        return Weather(o.getDouble("t"), o.getDouble("h"), o.getString("ciudad"), o.getLong("instante"))
    }

    fun guardarClima(c: Weather) {
        val o = JSONObject().put("t", c.temperatureC).put("h", c.humidityPct).put("ciudad", c.city).put("instante", c.timestamp)
        prefs.edit().putString(CLIMA, o.toString()).apply()
    }

    // ---------- Sesión activa (sobrevive aunque Android cierre la app) ----------
    fun sesionActiva(): ActiveSession? {
        val texto = prefs.getString(SESION_ACTIVA, null) ?: return null
        val o = JSONObject(texto)
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

    fun guardarSesionActiva(s: ActiveSession?) {
        if (s == null) {
            prefs.edit().remove(SESION_ACTIVA).apply()
            return
        }
        val o = JSONObject()
            .put("tipo", s.type).put("inicio", s.startTime).put("proximaAlerta", s.nextAlertAt)
            .put("pausadoMs", s.pausedMs).put("tomadoMl", s.drankMl).put("recomendadoMl", s.recommendedMl)
            .put("fcSuma", s.heartRateSum).put("fcLecturas", s.heartRateReadings)
        s.pausedSince?.let { o.put("pausadaDesde", it) }
        prefs.edit().putString(SESION_ACTIVA, o.toString()).apply()
    }

    // ---------- Alerta pendiente ----------
    fun alertaPendiente(): IntakeAlert? {
        val texto = prefs.getString(ALERTA, null) ?: return null
        val o = JSONObject(texto)
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

    fun guardarAlerta(a: IntakeAlert?) {
        if (a == null) {
            prefs.edit().remove(ALERTA).apply()
            return
        }
        val reasons = JSONArray()
        a.factor.reasons.forEach { reasons.put(it) }
        val o = JSONObject().put("factor", a.factor.value).put("razones", reasons).put("instante", a.timestamp)
        a.ml?.let { o.put("ml", it) }
        prefs.edit().putString(ALERTA, o.toString()).apply()
    }

    // ---------- Últimas lecturas de sensores (para calcular la alerta con la app cerrada) ----------
    fun ultimaFc(): Int? = if (prefs.contains(ULTIMA_FC)) prefs.getInt(ULTIMA_FC, 0) else null

    fun ultimaAltitud(): Double? =
        if (prefs.contains(ULTIMA_ALTITUD)) prefs.getFloat(ULTIMA_ALTITUD, 0f).toDouble() else null

    fun guardarLecturas(fc: Int?, altitud: Double?) {
        val e = prefs.edit()
        if (fc != null) e.putInt(ULTIMA_FC, fc)
        if (altitud != null) e.putFloat(ULTIMA_ALTITUD, altitud.toFloat())
        e.apply()
    }

    // ---------- Avisos de cambios (la app se actualiza cuando la alarma cambia algo) ----------
    fun escuchar(oyente: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(oyente)

    fun dejarDeEscuchar(oyente: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(oyente)

    // ---------- Utilidades ----------
    private fun <T> leerLista(clave: String, convertir: (JSONObject) -> T): List<T> {
        val texto = prefs.getString(clave, null) ?: return emptyList()
        val arreglo = JSONArray(texto)
        return (0 until arreglo.length()).map { convertir(arreglo.getJSONObject(it)) }
    }

    private fun <T> guardarLista(clave: String, lista: List<T>, convertir: (T) -> JSONObject) {
        val arreglo = JSONArray()
        lista.forEach { arreglo.put(convertir(it)) }
        prefs.edit().putString(clave, arreglo.toString()).apply()
    }

    private fun JSONObject.dobleONulo(clave: String): Double? =
        if (has(clave) && !isNull(clave)) getDouble(clave) else null

    private fun JSONObject.aCondiciones() = Conditions(
        altitudeM = dobleONulo("altitudM"),
        temperatureC = dobleONulo("temperaturaC"),
        humidityPct = dobleONulo("humedadPct"),
    )

    private fun Conditions.aJson(): JSONObject {
        val o = JSONObject()
        altitudeM?.let { o.put("altitudM", it) }
        temperatureC?.let { o.put("temperaturaC", it) }
        humidityPct?.let { o.put("humedadPct", it) }
        return o
    }

    companion object {
        /** Claves que cambian con cada lectura de sensor: la app no necesita recargarse por ellas. */
        val CLAVES_LECTURAS = setOf("ultima_fc", "ultima_altitud")

        const val PERFIL = "perfil"
        const val TOMAS = "tomas"
        const val SESIONES = "sesiones"
        const val CLIMA = "clima"
        const val SESION_ACTIVA = "sesion_activa"
        const val ALERTA = "alerta"
        const val ULTIMA_FC = "ultima_fc"
        const val ULTIMA_ALTITUD = "ultima_altitud"
        const val MAX_TOMAS = 2000
        const val MAX_SESIONES = 300
    }
}
