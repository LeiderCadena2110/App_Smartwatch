package co.edu.ucc.hidrasmart.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Revisa lo que necesita la alerta para salir sobre la esfera con la app cerrada. */
object AlertDiagnostics {

    /** Notificaciones permitidas para la app y el canal de alertas con importancia alta. */
    fun notificationsEnabled(contexto: Context): Boolean {
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return false
        AlarmScheduler.createChannel(contexto)
        val channel = contexto.getSystemService(NotificationManager::class.java)?.getNotificationChannel(AlarmScheduler.CHANNEL_ID)
        return channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun exactAlarmsAllowed(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return contexto.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    /** Desde Android 14 la pantalla completa puede estar desactivada para la app. */
    fun fullScreenAllowed(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return contexto.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    }

    fun allGood(contexto: Context) = notificationsEnabled(contexto) && exactAlarmsAllowed(contexto) && fullScreenAllowed(contexto)

    fun openNotificationSettings(contexto: Context) {
        startSettings(
            contexto,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
        )
    }

    fun openAlarmSettings(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSettings(contexto, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")))
        }
    }

    fun openFullScreenSettings(contexto: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            startSettings(contexto, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${contexto.packageName}")))
        }
    }

    /** Si el reloj no tiene esa pantalla de ajustes, abre la información de la app. */
    private fun startSettings(contexto: Context, intent: Intent) {
        try {
            contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            try {
                contexto.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
