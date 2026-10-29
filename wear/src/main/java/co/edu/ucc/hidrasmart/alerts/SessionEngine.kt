package co.edu.ucc.hidrasmart.alerts

import android.content.Context
import co.edu.ucc.hidrasmart.data.HydrationRepository
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
    private fun intervaloMs(repo: HydrationRepository) = repo.profile().intervalMin * 60_000L

    fun iniciar(contexto: Context, type: String) {
        val repo = HydrationRepository(contexto)
        if (repo.activeSession() != null) return
        val t = ahora()
        val sesion = ActiveSession(type = type, startTime = t, nextAlertAt = t + intervaloMs(repo))
        repo.saveAlert(null)
        repo.saveActiveSession(sesion)
        Alarmas.programar(contexto, sesion.nextAlertAt)
    }

    /** Llega la hora: calcula los ml, guarda la alerta, avisa y programa la siguiente. */
    fun dispararAlerta(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val sesion = repo.activeSession() ?: return
        if (sesion.pausedSince != null) return
        val t = ahora()
        val siguiente = t + intervaloMs(repo)

        val pendiente = repo.pendingAlert()
        if (pendiente != null) {
            // No respondió la anterior: se recuerda la misma, sin sumar otra recomendación
            repo.saveActiveSession(sesion.copy(nextAlertAt = siguiente))
            Alarmas.programar(contexto, siguiente)
            Alarmas.notificar(contexto, pendiente)
            return
        }

        val profile = repo.profile()
        val clima = repo.lastWeather()
        val condiciones = Conditions(
            altitudeM = repo.lastAltitude() ?: profile.usualAltitudeM,
            temperatureC = clima?.temperatureC ?: profile.manualTemperatureC,
            humidityPct = clima?.humidityPct ?: profile.manualHumidityPct,
            heartRateBpm = repo.lastHeartRate(),
        )
        val factor = HydrationCalculator.factor(condiciones, profile.testConditions, profile.age)
        val ml = profile.sweatRateLh?.let { HydrationCalculator.mlPerAlert(it, factor.value, profile.intervalMin) }
        val alerta = IntakeAlert(ml, factor, t)

        repo.saveAlert(alerta)
        repo.saveActiveSession(sesion.copy(recommendedMl = sesion.recommendedMl + (ml ?: 0), nextAlertAt = siguiente))
        Alarmas.programar(contexto, siguiente)
        Alarmas.notificar(contexto, alerta)
    }

    /** Prueba real: la alarma del sistema suena en 10 s, para comprobarla con la app cerrada. */
    fun programarPrueba(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val sesion = repo.activeSession() ?: return
        if (sesion.pausedSince != null) return
        val cuando = ahora() + 10_000L
        repo.saveActiveSession(sesion.copy(nextAlertAt = cuando))
        Alarmas.programar(contexto, cuando)
    }

    /** "Ya tomé": registra los ml y la próxima alerta queda a un intervalo de ahora. */
    fun yaTome(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val alerta = repo.pendingAlert() ?: return
        alerta.ml?.let { registrarToma(contexto, it) }
        repo.saveAlert(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.activeSession() ?: return
        val siguiente = ahora() + intervaloMs(repo)
        repo.saveActiveSession(sesion.copy(nextAlertAt = siguiente))
        Alarmas.programar(contexto, siguiente)
    }

    /** "En 5 min": esa recomendación no cuenta todavía. */
    fun posponer(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val alerta = repo.pendingAlert() ?: return
        repo.saveAlert(null)
        Alarmas.quitarNotificacion(contexto)
        val sesion = repo.activeSession() ?: return
        val siguiente = ahora() + POSPONER_MS
        repo.saveActiveSession(
            sesion.copy(nextAlertAt = siguiente, recommendedMl = (sesion.recommendedMl - (alerta.ml ?: 0)).coerceAtLeast(0)),
        )
        Alarmas.programar(contexto, siguiente)
    }

    fun registrarToma(contexto: Context, ml: Int) {
        val repo = HydrationRepository(contexto)
        repo.addIntake(Intake(ahora(), ml))
        repo.activeSession()?.let { repo.saveActiveSession(it.copy(drankMl = it.drankMl + ml)) }
    }

    fun pausarOReanudar(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val sesion = repo.activeSession() ?: return
        val t = ahora()
        val pausedSince = sesion.pausedSince
        if (pausedSince == null) {
            repo.saveActiveSession(sesion.copy(pausedSince = t))
            Alarmas.cancelar(contexto)
        } else {
            val pausa = t - pausedSince
            val reanudada = sesion.copy(pausedSince = null, pausedMs = sesion.pausedMs + pausa, nextAlertAt = sesion.nextAlertAt + pausa)
            repo.saveActiveSession(reanudada)
            Alarmas.programar(contexto, reanudada.nextAlertAt)
        }
    }

    /** Suma un bloque de lecturas de frecuencia cardíaca al promedio de la sesión. */
    fun sumarFc(contexto: Context, suma: Long, lecturas: Int) {
        if (lecturas <= 0) return
        val repo = HydrationRepository(contexto)
        val sesion = repo.activeSession() ?: return
        repo.saveActiveSession(sesion.copy(heartRateSum = sesion.heartRateSum + suma, heartRateReadings = sesion.heartRateReadings + lecturas))
    }

    /** Si cambia el intervalo durante la sesión, la próxima alerta se ajusta de una vez. */
    fun ajustarIntervalo(contexto: Context) {
        val repo = HydrationRepository(contexto)
        val sesion = repo.activeSession() ?: return
        if (sesion.pausedSince != null) return
        val conNuevo = ahora() + intervaloMs(repo)
        if (conNuevo < sesion.nextAlertAt) {
            repo.saveActiveSession(sesion.copy(nextAlertAt = conNuevo))
            Alarmas.programar(contexto, conNuevo)
        }
    }

    /** Termina, guarda en el historial y devuelve el id. */
    fun terminar(contexto: Context): Long? {
        val repo = HydrationRepository(contexto)
        val s = repo.activeSession() ?: return null
        val t = ahora()
        Alarmas.cancelar(contexto)
        Alarmas.quitarNotificacion(contexto)
        repo.saveAlert(null)
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
        repo.saveSession(guardada)
        repo.saveActiveSession(null)
        return guardada.id
    }
}
