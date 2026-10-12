package co.edu.ucc.hidrasmart.alertas

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
object Diagnostico {

    /** Notificaciones permitidas para la app y el canal de alertas con importancia alta. */
    fun notificaciones(contexto: Context): Boolean {
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return false
        Alarmas.crearCanal(contexto)
        val canal = contexto.getSystemService(NotificationManager::class.java)?.getNotificationChannel(Alarmas.CANAL)
        return canal == null || canal.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun alarmasExactas(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return contexto.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    /** Desde Android 14 la pantalla completa puede estar desactivada para la app. */
    fun pantallaCompleta(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return contexto.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    }

    fun todoBien(contexto: Context) = notificaciones(contexto) && alarmasExactas(contexto) && pantallaCompleta(contexto)

    fun abrirAjustesNotificaciones(contexto: Context) {
        abrir(
            contexto,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
        )
    }

    fun abrirAjustesAlarmas(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            abrir(contexto, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${contexto.packageName}")))
        }
    }

    fun abrirAjustesPantallaCompleta(contexto: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            abrir(contexto, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${contexto.packageName}")))
        }
    }

    /** Si el reloj no tiene esa pantalla de ajustes, abre la información de la app. */
    private fun abrir(contexto: Context, intent: Intent) {
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
