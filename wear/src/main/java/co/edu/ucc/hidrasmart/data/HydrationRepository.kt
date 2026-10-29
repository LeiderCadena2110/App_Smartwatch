package co.edu.ucc.hidrasmart.data

import android.content.Context
import android.content.SharedPreferences
import co.edu.ucc.hidrasmart.domain.AlertaToma
import co.edu.ucc.hidrasmart.domain.Clima
import co.edu.ucc.hidrasmart.domain.Condiciones
import co.edu.ucc.hidrasmart.domain.Factor
import co.edu.ucc.hidrasmart.domain.Perfil
import co.edu.ucc.hidrasmart.domain.SesionEnCurso
import co.edu.ucc.hidrasmart.domain.SesionGuardada
import co.edu.ucc.hidrasmart.domain.Toma
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

    fun perfil(): Perfil {
        val texto = prefs.getString(PERFIL, null) ?: return Perfil()
        val o = JSONObject(texto)
        return Perfil(
            pesoKg = o.optDouble("pesoKg", 70.0),
            edad = o.optInt("edad", 22),
            metaDiariaMl = o.optInt("metaDiariaMl", 2000),
            intervaloMin = o.optInt("intervaloMin", 15),
            ciudad = o.optString("ciudad", "Pasto,CO"),
            altitudHabitualM = o.optDouble("altitudHabitualM", 2527.0),
            temperaturaManualC = o.optDouble("temperaturaManualC", 14.0),
            humedadManualPct = o.optDouble("humedadManualPct", 75.0),
            tasaSudoracionLh = o.dobleONulo("tasaSudoracionLh"),
            condicionesPrueba = o.optJSONObject("condicionesPrueba")?.aCondiciones(),
        )
    }

    fun guardarPerfil(p: Perfil) {
        val o = JSONObject()
            .put("pesoKg", p.pesoKg)
            .put("edad", p.edad)
            .put("metaDiariaMl", p.metaDiariaMl)
            .put("intervaloMin", p.intervaloMin)
            .put("ciudad", p.ciudad)
            .put("altitudHabitualM", p.altitudHabitualM)
            .put("temperaturaManualC", p.temperaturaManualC)
            .put("humedadManualPct", p.humedadManualPct)
        p.tasaSudoracionLh?.let { o.put("tasaSudoracionLh", it) }
        p.condicionesPrueba?.let { o.put("condicionesPrueba", it.aJson()) }
        prefs.edit().putString(PERFIL, o.toString()).apply()
    }

    // ---------- Tomas de agua ----------
    fun tomas(): List<Toma> = leerLista(TOMAS) { Toma(it.getLong("instante"), it.getInt("ml")) }

    fun agregarToma(t: Toma) {
        val lista = tomas() + t
        guardarLista(TOMAS, lista.takeLast(MAX_TOMAS)) { JSONObject().put("instante", it.instante).put("ml", it.ml) }
    }

    // ---------- Sesiones de entrenamiento ----------
    fun sesiones(): List<SesionGuardada> = leerLista(SESIONES) {
        SesionGuardada(
            id = it.getLong("id"),
            tipo = it.getString("tipo"),
            inicio = it.getLong("inicio"),
            fin = it.getLong("fin"),
            duracionSeg = it.getLong("duracionSeg"),
            fcMedia = if (it.has("fcMedia")) it.getInt("fcMedia") else null,
            tomadoMl = it.getInt("tomadoMl"),
            recomendadoMl = it.getInt("recomendadoMl"),
        )
    }

    fun guardarSesion(s: SesionGuardada) {
        val lista = sesiones() + s
        guardarLista(SESIONES, lista.takeLast(MAX_SESIONES)) {
            val o = JSONObject()
                .put("id", it.id).put("tipo", it.tipo).put("inicio", it.inicio).put("fin", it.fin)
                .put("duracionSeg", it.duracionSeg).put("tomadoMl", it.tomadoMl).put("recomendadoMl", it.recomendadoMl)
            it.fcMedia?.let { fc -> o.put("fcMedia", fc) }
            o
        }
    }

    // ---------- Clima (último valor, para usarlo sin conexión) ----------
    fun ultimoClima(): Clima? {
        val texto = prefs.getString(CLIMA, null) ?: return null
        val o = JSONObject(texto)
        return Clima(o.getDouble("t"), o.getDouble("h"), o.getString("ciudad"), o.getLong("instante"))
    }

    fun guardarClima(c: Clima) {
        val o = JSONObject().put("t", c.temperaturaC).put("h", c.humedadPct).put("ciudad", c.ciudad).put("instante", c.instante)
        prefs.edit().putString(CLIMA, o.toString()).apply()
    }

    // ---------- Sesión activa (sobrevive aunque Android cierre la app) ----------
    fun sesionActiva(): SesionEnCurso? {
        val texto = prefs.getString(SESION_ACTIVA, null) ?: return null
        val o = JSONObject(texto)
        return SesionEnCurso(
            tipo = o.getString("tipo"),
            inicio = o.getLong("inicio"),
            proximaAlerta = o.getLong("proximaAlerta"),
            pausadaDesde = if (o.has("pausadaDesde")) o.getLong("pausadaDesde") else null,
            pausadoMs = o.optLong("pausadoMs", 0),
            tomadoMl = o.optInt("tomadoMl", 0),
            recomendadoMl = o.optInt("recomendadoMl", 0),
            fcSuma = o.optLong("fcSuma", 0),
            fcLecturas = o.optInt("fcLecturas", 0),
        )
    }

    fun guardarSesionActiva(s: SesionEnCurso?) {
        if (s == null) {
            prefs.edit().remove(SESION_ACTIVA).apply()
            return
        }
        val o = JSONObject()
            .put("tipo", s.tipo).put("inicio", s.inicio).put("proximaAlerta", s.proximaAlerta)
            .put("pausadoMs", s.pausadoMs).put("tomadoMl", s.tomadoMl).put("recomendadoMl", s.recomendadoMl)
            .put("fcSuma", s.fcSuma).put("fcLecturas", s.fcLecturas)
        s.pausadaDesde?.let { o.put("pausadaDesde", it) }
        prefs.edit().putString(SESION_ACTIVA, o.toString()).apply()
    }

    // ---------- Alerta pendiente ----------
    fun alertaPendiente(): AlertaToma? {
        val texto = prefs.getString(ALERTA, null) ?: return null
        val o = JSONObject(texto)
        val razones = o.optJSONArray("razones")
        return AlertaToma(
            ml = if (o.has("ml")) o.getInt("ml") else null,
            factor = Factor(
                valor = o.optDouble("factor", 1.0),
                razones = if (razones == null) emptyList() else (0 until razones.length()).map { razones.getString(it) },
            ),
            instante = o.optLong("instante", 0),
        )
    }

    fun guardarAlerta(a: AlertaToma?) {
        if (a == null) {
            prefs.edit().remove(ALERTA).apply()
            return
        }
        val razones = JSONArray()
        a.factor.razones.forEach { razones.put(it) }
        val o = JSONObject().put("factor", a.factor.valor).put("razones", razones).put("instante", a.instante)
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

    private fun JSONObject.aCondiciones() = Condiciones(
        altitudM = dobleONulo("altitudM"),
        temperaturaC = dobleONulo("temperaturaC"),
        humedadPct = dobleONulo("humedadPct"),
    )

    private fun Condiciones.aJson(): JSONObject {
        val o = JSONObject()
        altitudM?.let { o.put("altitudM", it) }
        temperaturaC?.let { o.put("temperaturaC", it) }
        humedadPct?.let { o.put("humedadPct", it) }
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
