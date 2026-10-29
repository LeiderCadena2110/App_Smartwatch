package co.edu.ucc.hidrasmart.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.edu.ucc.hidrasmart.MainActivity
import co.edu.ucc.hidrasmart.R
import co.edu.ucc.hidrasmart.domain.IntakeAlert

/**
 * Alarmas del sistema y notificación de alerta.
 * La alarma despierta el reloj aunque la app esté cerrada o la pantalla apagada.
 */
object AlarmScheduler {

    const val ACTION_ALARM = "co.edu.ucc.hidrasmart.ALARMA"
    const val ACTION_DRANK = "co.edu.ucc.hidrasmart.YA_TOME"
    const val ACTION_SNOOZE = "co.edu.ucc.hidrasmart.POSPONER"

    // Canal nuevo (v2) para que tome sonido de alarma e importancia alta aunque exista el anterior
    const val CHANNEL_ID = "alertas_hidratacion_v2"
    private const val LEGACY_CHANNEL_ID = "alertas_hidratacion"
    private const val NOTIFICATION_ID = 1001
    private val VIBRATION_PATTERN = longArrayOf(0, 500, 250, 500, 250, 800)

    /** Programa la próxima alerta a una hora exacta. */
    fun schedule(context: Context, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = receiverIntent(context, ACTION_ALARM, 1)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            // Las alarmas tipo "despertador" no se retrasan aunque el reloj esté en reposo
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, openAppIntent(context)), trigger)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, trigger)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(receiverIntent(context, ACTION_ALARM, 1))
    }

    /** Muestra la alerta sobre la esfera, con sonido, vibración y botones de respuesta. */
    @SuppressLint("MissingPermission")
    fun showAlert(context: Context, alert: IntakeAlert) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val text = alert.ml?.let { "Toma $it ml de agua" } ?: "Toma agua según tu sed"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gota)
            .setContentTitle("¡Hora de hidratarte!")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION_PATTERN)
            .setOngoing(true)
            .setContentIntent(alertScreenIntent(context))
            .setFullScreenIntent(alertScreenIntent(context), true)
            .addAction(R.drawable.ic_gota, "Ya tomé", receiverIntent(context, ACTION_DRANK, 2))
            .addAction(R.drawable.ic_gota, "En 5 min", receiverIntent(context, ACTION_SNOOZE, 3))
            .build()
        try {
            // Quitar la anterior para que vuelva a sonar y a abrir la pantalla completa
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la alerta se verá al abrir la app
        }
    }

    /**
     * Abre la pantalla de alerta encima de la esfera. Si Android no deja abrirla desde
     * segundo plano, la notificación de pantalla completa se encarga.
     */
    fun openAlertScreen(context: Context) {
        if (MainActivity.visible || AlertActivity.visible) return
        try {
            context.startActivity(
                Intent(context, AlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (e: Exception) {
            // Bloqueado por el sistema: queda la notificación
        }
    }

    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Alertas de hidratación", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa cuándo y cuánto tomar agua durante el entrenamiento"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java)?.apply {
            deleteNotificationChannel(LEGACY_CHANNEL_ID)
            createNotificationChannel(channel)
        }
    }

    private fun receiverIntent(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AlertReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alertScreenIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            4,
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
