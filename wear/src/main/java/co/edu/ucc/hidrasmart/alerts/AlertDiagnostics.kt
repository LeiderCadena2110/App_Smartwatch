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
    fun notificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        AlarmScheduler.createChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(AlarmScheduler.CHANNEL_ID)
        return channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    /** Desde Android 14 la pantalla completa puede estar desactivada para la app. */
    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    }

    fun allGood(context: Context) = notificationsEnabled(context) && exactAlarmsAllowed(context) && fullScreenAllowed(context)

    fun openNotificationSettings(context: Context) {
        startSettings(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    fun openAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            startSettings(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }

    /** Si el reloj no tiene esa pantalla de ajustes, abre la información de la app. */
    private fun startSettings(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
