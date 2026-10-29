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
object SessionEngine {

    private const val SNOOZE_MS = 5 * 60_000L

    private fun now() = System.currentTimeMillis()
    private fun intervalMs(repo: HydrationRepository) = repo.profile().intervalMin * 60_000L

    fun start(context: Context, type: String) {
        val repo = HydrationRepository(context)
        if (repo.activeSession() != null) return
        val t = now()
        val session = ActiveSession(type = type, startTime = t, nextAlertAt = t + intervalMs(repo))
        repo.saveAlert(null)
        repo.saveActiveSession(session)
        AlarmScheduler.schedule(context, session.nextAlertAt)
    }

    /** Llega la hora: calcula los ml, guarda la alerta, avisa y programa la siguiente. */
    fun fireAlert(context: Context) {
        val repo = HydrationRepository(context)
        val session = repo.activeSession() ?: return
        if (session.pausedSince != null) return
        val t = now()
        val next = t + intervalMs(repo)

        val pending = repo.pendingAlert()
        if (pending != null) {
            // No respondió la anterior: se recuerda la misma, sin sumar otra recomendación
            repo.saveActiveSession(session.copy(nextAlertAt = next))
            AlarmScheduler.schedule(context, next)
            AlarmScheduler.showAlert(context, pending)
            return
        }

        val profile = repo.profile()
        val weather = repo.lastWeather()
        val conditions = Conditions(
            altitudeM = repo.lastAltitude() ?: profile.usualAltitudeM,
            temperatureC = weather?.temperatureC ?: profile.manualTemperatureC,
            humidityPct = weather?.humidityPct ?: profile.manualHumidityPct,
            heartRateBpm = repo.lastHeartRate(),
        )
        val factor = HydrationCalculator.factor(conditions, profile.testConditions, profile.age)
        val ml = profile.sweatRateLh?.let { HydrationCalculator.mlPerAlert(it, factor.value, profile.intervalMin) }
        val alert = IntakeAlert(ml, factor, t)

        repo.saveAlert(alert)
        repo.saveActiveSession(session.copy(recommendedMl = session.recommendedMl + (ml ?: 0), nextAlertAt = next))
        AlarmScheduler.schedule(context, next)
        AlarmScheduler.showAlert(context, alert)
    }

    /** Prueba real: la alarma del sistema suena en 10 s, para comprobarla con la app cerrada. */
    fun scheduleTest(context: Context) {
        val repo = HydrationRepository(context)
        val session = repo.activeSession() ?: return
        if (session.pausedSince != null) return
        val triggerAt = now() + 10_000L
        repo.saveActiveSession(session.copy(nextAlertAt = triggerAt))
        AlarmScheduler.schedule(context, triggerAt)
    }

    /** "Ya tomé": registra los ml y la próxima alerta queda a un intervalo de ahora. */
    fun markDrank(context: Context) {
        val repo = HydrationRepository(context)
        val alert = repo.pendingAlert() ?: return
        alert.ml?.let { logIntake(context, it) }
        repo.saveAlert(null)
        AlarmScheduler.dismissNotification(context)
        val session = repo.activeSession() ?: return
        val next = now() + intervalMs(repo)
        repo.saveActiveSession(session.copy(nextAlertAt = next))
        AlarmScheduler.schedule(context, next)
    }

    /** "En 5 min": esa recomendación no cuenta todavía. */
    fun snooze(context: Context) {
        val repo = HydrationRepository(context)
        val alert = repo.pendingAlert() ?: return
        repo.saveAlert(null)
        AlarmScheduler.dismissNotification(context)
        val session = repo.activeSession() ?: return
        val next = now() + SNOOZE_MS
        repo.saveActiveSession(
            session.copy(nextAlertAt = next, recommendedMl = (session.recommendedMl - (alert.ml ?: 0)).coerceAtLeast(0)),
        )
        AlarmScheduler.schedule(context, next)
    }

    fun logIntake(context: Context, ml: Int) {
        val repo = HydrationRepository(context)
        repo.addIntake(Intake(now(), ml))
        repo.activeSession()?.let { repo.saveActiveSession(it.copy(drankMl = it.drankMl + ml)) }
    }

    fun togglePause(context: Context) {
        val repo = HydrationRepository(context)
        val session = repo.activeSession() ?: return
        val t = now()
        val pausedSince = session.pausedSince
        if (pausedSince == null) {
            repo.saveActiveSession(session.copy(pausedSince = t))
            AlarmScheduler.cancel(context)
        } else {
            val pauseMs = t - pausedSince
            val resumed = session.copy(pausedSince = null, pausedMs = session.pausedMs + pauseMs, nextAlertAt = session.nextAlertAt + pauseMs)
            repo.saveActiveSession(resumed)
            AlarmScheduler.schedule(context, resumed.nextAlertAt)
        }
    }

    /** Suma un bloque de lecturas de frecuencia cardíaca al promedio de la sesión. */
    fun addHeartRate(context: Context, sum: Long, readings: Int) {
        if (readings <= 0) return
        val repo = HydrationRepository(context)
        val session = repo.activeSession() ?: return
        repo.saveActiveSession(session.copy(heartRateSum = session.heartRateSum + sum, heartRateReadings = session.heartRateReadings + readings))
    }

    /** Si cambia el intervalo durante la sesión, la próxima alerta se ajusta de una vez. */
    fun adjustInterval(context: Context) {
        val repo = HydrationRepository(context)
        val session = repo.activeSession() ?: return
        if (session.pausedSince != null) return
        val withNew = now() + intervalMs(repo)
        if (withNew < session.nextAlertAt) {
            repo.saveActiveSession(session.copy(nextAlertAt = withNew))
            AlarmScheduler.schedule(context, withNew)
        }
    }

    /** Termina, guarda en el historial y devuelve el id. */
    fun end(context: Context): Long? {
        val repo = HydrationRepository(context)
        val s = repo.activeSession() ?: return null
        val t = now()
        AlarmScheduler.cancel(context)
        AlarmScheduler.dismissNotification(context)
        repo.saveAlert(null)
        val saved = SavedSession(
            id = t,
            type = s.type,
            startTime = s.startTime,
            endTime = t,
            durationSec = (((s.pausedSince ?: t) - s.startTime - s.pausedMs) / 1000).coerceAtLeast(0),
            avgHeartRate = if (s.heartRateReadings > 0) (s.heartRateSum / s.heartRateReadings).toInt() else null,
            drankMl = s.drankMl,
            recommendedMl = s.recommendedMl.coerceAtLeast(0),
        )
        repo.saveSession(saved)
        repo.saveActiveSession(null)
        return saved.id
    }
}
