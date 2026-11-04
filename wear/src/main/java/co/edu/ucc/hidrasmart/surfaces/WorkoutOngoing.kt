package co.edu.ucc.hidrasmart.surfaces

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import co.edu.ucc.hidrasmart.MainActivity
import co.edu.ucc.hidrasmart.R
import co.edu.ucc.hidrasmart.data.HydrationRepository

/**
 * Actividad en curso de Wear OS mientras hay un entrenamiento: un ícono de cronómetro
 * en la esfera y en "Recientes" con el tiempo transcurrido. Tocarlo vuelve a la sesión.
 */
object WorkoutOngoing {

    private const val CHANNEL_ID = "entrenamiento_en_curso"
    private const val NOTIFICATION_ID = 1002

    /** Muestra o actualiza la actividad (al empezar, pausar o reanudar). */
    fun show(context: Context) {
        val session = HydrationRepository(context).activeSession() ?: return hide(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val paused = session.pausedSince != null
        val openApp = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setContentTitle(context.getString(R.string.ongoing_title, session.type))
            .setContentText(context.getString(if (paused) R.string.ongoing_paused else R.string.ongoing_running))
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)

        // El cronómetro cuenta desde el inicio sin contar las pausas (SystemClock.elapsedRealtime como base)
        val status = if (paused) {
            Status.Builder()
                .addTemplate(context.getString(R.string.ongoing_status_paused))
                .addPart("tipo", Status.TextPart(session.type))
                .build()
        } else {
            val activeMs = (System.currentTimeMillis() - session.startTime - session.pausedMs).coerceAtLeast(0)
            Status.Builder()
                .addTemplate(context.getString(R.string.ongoing_status_running))
                .addPart("tipo", Status.TextPart(session.type))
                .addPart("tiempo", Status.StopwatchPart(SystemClock.elapsedRealtime() - activeMs))
                .build()
        }

        OngoingActivity.Builder(context, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_stopwatch)
            .setTouchIntent(openApp)
            .setStatus(status)
            .build()
            .apply(context)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build())
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: el entrenamiento sigue funcionando igual
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ongoing_channel_name),
            NotificationManager.IMPORTANCE_LOW, // sin sonido: solo muestra el entrenamiento en curso
        ).apply { description = context.getString(R.string.ongoing_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
