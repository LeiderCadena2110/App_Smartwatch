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
object Alarmas {

    const val ACCION_ALARMA = "co.edu.ucc.hidrasmart.ALARMA"
    const val ACCION_YA_TOME = "co.edu.ucc.hidrasmart.YA_TOME"
    const val ACCION_POSPONER = "co.edu.ucc.hidrasmart.POSPONER"

    // Canal nuevo (v2) para que tome sonido de alarma e importancia alta aunque exista el anterior
    const val CANAL = "alertas_hidratacion_v2"
    private const val CANAL_VIEJO = "alertas_hidratacion"
    private const val ID_NOTIFICACION = 1001
    private val PATRON_VIBRACION = longArrayOf(0, 500, 250, 500, 250, 800)

    /** Programa la próxima alerta a una hora exacta. */
    fun programar(contexto: Context, cuando: Long) {
        val alarmas = contexto.getSystemService(AlarmManager::class.java) ?: return
        val disparo = intentReceptor(contexto, ACCION_ALARMA, 1)
        val puedeExacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmas.canScheduleExactAlarms()
        if (puedeExacta) {
            // Las alarmas tipo "despertador" no se retrasan aunque el reloj esté en reposo
            alarmas.setAlarmClock(AlarmManager.AlarmClockInfo(cuando, abrirApp(contexto)), disparo)
        } else {
            alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, disparo)
        }
    }

    fun cancelar(contexto: Context) {
        contexto.getSystemService(AlarmManager::class.java)?.cancel(intentReceptor(contexto, ACCION_ALARMA, 1))
    }

    /** Muestra la alerta sobre la esfera, con sonido, vibración y botones de respuesta. */
    @SuppressLint("MissingPermission")
    fun notificar(contexto: Context, alerta: IntakeAlert) {
        crearCanal(contexto)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val text = alerta.ml?.let { "Toma $it ml de agua" } ?: "Toma agua según tu sed"
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_gota)
            .setContentTitle("¡Hora de hidratarte!")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(PATRON_VIBRACION)
            .setOngoing(true)
            .setContentIntent(intentPantallaAlerta(contexto))
            .setFullScreenIntent(intentPantallaAlerta(contexto), true)
            .addAction(R.drawable.ic_gota, "Ya tomé", intentReceptor(contexto, ACCION_YA_TOME, 2))
            .addAction(R.drawable.ic_gota, "En 5 min", intentReceptor(contexto, ACCION_POSPONER, 3))
            .build()
        try {
            // Quitar la anterior para que vuelva a sonar y a abrir la pantalla completa
            NotificationManagerCompat.from(contexto).cancel(ID_NOTIFICACION)
            NotificationManagerCompat.from(contexto).notify(ID_NOTIFICACION, notificacion)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la alerta se verá al abrir la app
        }
    }

    /**
     * Abre la pantalla de alerta encima de la esfera. Si Android no deja abrirla desde
     * segundo plano, la notificación de pantalla completa se encarga.
     */
    fun abrirPantallaAlerta(contexto: Context) {
        if (MainActivity.visible || AlertaActivity.visible) return
        try {
            contexto.startActivity(
                Intent(contexto, AlertaActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (e: Exception) {
            // Bloqueado por el sistema: queda la notificación
        }
    }

    fun quitarNotificacion(contexto: Context) {
        NotificationManagerCompat.from(contexto).cancel(ID_NOTIFICACION)
    }

    fun crearCanal(contexto: Context) {
        val canal = NotificationChannel(CANAL, "Alertas de hidratación", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa cuándo y cuánto tomar agua durante el entrenamiento"
            enableVibration(true)
            vibrationPattern = PATRON_VIBRACION
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        contexto.getSystemService(NotificationManager::class.java)?.apply {
            deleteNotificationChannel(CANAL_VIEJO)
            createNotificationChannel(canal)
        }
    }

    private fun intentReceptor(contexto: Context, accion: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            code,
            Intent(contexto, AlertaReceiver::class.java).setAction(accion),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun intentPantallaAlerta(contexto: Context): PendingIntent =
        PendingIntent.getActivity(
            contexto,
            4,
            Intent(contexto, AlertaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun abrirApp(contexto: Context): PendingIntent =
        PendingIntent.getActivity(
            contexto,
            0,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
