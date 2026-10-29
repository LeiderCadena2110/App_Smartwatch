package co.edu.ucc.hidrasmart.alerts

import android.content.Context
import co.edu.ucc.hidrasmart.data.Repositorio
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Conditions
import co.edu.ucc.hidrasmart.domain.ActiveSession
import co.edu.ucc.hidrasmart.domain.SavedSession
import co.edu.ucc.hidrasmart.domain.Intake

/**
 * Reglas del entrenamiento y de las alertas. Todo se guarda en el reloj, así funciona
 * igual desde la app abierta, desde la alarma o desde los botones de la notificación.
 */
object MotorSesion {

    private const val POSPONER_MS = 5 * 60_000L

    private fun ahora() = System.currentTimeMillis()
    private fun intervaloMs(repo: Repositorio) = repo.perfil().intervalMin * 60_000L

    fun iniciar(contexto: Context, type: String) {
        val repo = Repositorio(contexto)
        if (repo.sesionActiva() != null) return
        val t = ahora()
        val sesion = ActiveSession(type = type, startTime = t, nextAlertAt = t + intervaloMs(repo))
        repo.guardarAlerta(null)
        repo.guardarSesionActiva(sesion)
        Alarmas.programar(contexto, sesion.nextAlertAt)
    }

    /** Llega la hora: calcula los ml, guarda la alerta, avisa y programa la siguiente. */
    fun dispararAlerta(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausedSince != null) return
        val t = ahora()
        val siguiente = t + intervaloMs(repo)

        val pendiente = repo.alertaPendiente()
        if (pendiente != null) {
            // No respondió la anterior: se recuerda la misma, sin sumar otra recomendación
            repo.guardarSesionActiva(sesion.copy(nextAlertAt = siguiente))
            Alarmas.programar(contexto, siguiente)
            Alarmas.notificar(contexto, pendiente)
            return
        }

        val perfil = repo.perfil()
        val clima = repo.ultimoClima()
        val condiciones = Conditions(
            altitudeM = repo.ultimaAltitud() ?: perfil.usualAltitudeM,
            temperatureC = clima?.temperatureC ?: perfil.manualTemperatureC,
            humidityPct = clima?.humidityPct ?: perfil.manualHumidityPct,
            heartRateBpm = repo.ultimaFc(),
        )
        val factor = HydrationCalculator.factor(condiciones, perfil.testConditions, perfil.age)
        val ml = perfil.sweatRateLh?.let { HydrationCalculator.mlPerAlert(it, factor.value, perfil.intervalMin) }
        val alerta = IntakeAlert(ml, factor, t)

        repo.guardarAlerta(alerta)
        repo.guardarSesionActiva(sesion.copy(recommendedMl = sesion.recommendedMl + (ml ?: 0), nextAlertAt = siguiente))
        Alarmas.programar(contexto, siguiente)
        Alarmas.notificar(contexto, alerta)
    }

    /** Prueba real: la alarma del sistema suena en 10 s, para comprobarla con la app cerrada. */
    fun programarPrueba(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausedSince != null) return
        val cuando = ahora() + 10_000L
        repo.guardarSesionActiva(sesion.copy(nextAlertAt = cuando))
        Alarmas.programar(contexto, cuando)
    }

    /** "Ya tomé": registra los ml y la próxima alerta queda a un intervalo de ahora. */
    fun yaTome(contexto: Context) {
        val repo = Repositorio(contexto)
        val alerta = repo.alertaPendiente() ?: return
        alerta.ml?.let { registrarToma(contexto, it) }
        repo.guardarAlerta(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.sesionActiva() ?: return
        val siguiente = ahora() + intervaloMs(repo)
        repo.guardarSesionActiva(sesion.copy(nextAlertAt = siguiente))
        Alarmas.programar(contexto, siguiente)
    }

    /** "En 5 min": esa recomendación no cuenta todavía. */
    fun posponer(contexto: Context) {
        val repo = Repositorio(contexto)
        val alerta = repo.alertaPendiente() ?: return
        repo.guardarAlerta(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.sesionActiva() ?: return
        val siguiente = ahora() + POSPONER_MS
        repo.guardarSesionActiva(
            sesion.copy(nextAlertAt = siguiente, recommendedMl = (sesion.recommendedMl - (alerta.ml ?: 0)).coerceAtLeast(0)),
        )
        Alarmas.programar(contexto, siguiente)
    }

    fun registrarToma(contexto: Context, ml: Int) {
        val repo = Repositorio(contexto)
        repo.agregarToma(Intake(ahora(), ml))
        repo.sesionActiva()?.let { repo.guardarSesionActiva(it.copy(drankMl = it.drankMl + ml)) }
    }

    fun pausarOReanudar(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        val t = ahora()
        val pausedSince = sesion.pausedSince
        if (pausedSince == null) {
            repo.guardarSesionActiva(sesion.copy(pausedSince = t))
            Alarmas.cancelar(contexto)
        } else {
            val pausa = t - pausedSince
            val reanudada = sesion.copy(pausedSince = null, pausedMs = sesion.pausedMs + pausa, nextAlertAt = sesion.nextAlertAt + pausa)
            repo.guardarSesionActiva(reanudada)
            Alarmas.programar(contexto, reanudada.nextAlertAt)
        }
    }

    /** Suma un bloque de lecturas de frecuencia cardíaca al promedio de la sesión. */
    fun sumarFc(contexto: Context, suma: Long, lecturas: Int) {
        if (lecturas <= 0) return
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        repo.guardarSesionActiva(sesion.copy(heartRateSum = sesion.heartRateSum + suma, heartRateReadings = sesion.heartRateReadings + lecturas))
    }

    /** Si cambia el intervalo durante la sesión, la próxima alerta se ajusta de una vez. */
    fun ajustarIntervalo(contexto: Context) {
        val repo = Repositorio(contexto)
        val sesion = repo.sesionActiva() ?: return
        if (sesion.pausedSince != null) return
        val conNuevo = ahora() + intervaloMs(repo)
        if (conNuevo < sesion.nextAlertAt) {
            repo.guardarSesionActiva(sesion.copy(nextAlertAt = conNuevo))
            Alarmas.programar(contexto, conNuevo)
        }
    }

    /** Termina, guarda en el historial y devuelve el id. */
    fun terminar(contexto: Context): Long? {
        val repo = Repositorio(contexto)
        val s = repo.sesionActiva() ?: return null
        val t = ahora()
        Alarmas.cancelar(contexto)
        Alarmas.quitarNotificacion(contexto)
        repo.guardarAlerta(null)
        val guardada = SavedSession(
            id = t,
            type = s.type,
            startTime = s.startTime,
            endTime = t,
            durationSec = (((s.pausedSince ?: t) - s.startTime - s.pausedMs) / 1000).coerceAtLeast(0),
            avgHeartRate = if (s.heartRateReadings > 0) (s.heartRateSum / s.heartRateReadings).toInt() else null,
            drankMl = s.drankMl,
            recommendedMl = s.recommendedMl.coerceAtLeast(0),
        )
        repo.guardarSesion(guardada)
        repo.guardarSesionActiva(null)
        return guardada.id
    }
}
