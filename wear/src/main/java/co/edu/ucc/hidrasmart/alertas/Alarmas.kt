package co.edu.ucc.hidrasmart.alertas

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
import co.edu.ucc.hidrasmart.dominio.AlertaToma

/**
 * Alarmas del sistema y notificación de alerta.
 * La alarma despierta el reloj aunque la app esté cerrada o la pantalla apagada.
 */
object Alarmas {

    const val ACCION_ALARMA = "co.edu.ucc.hidrasmart.ALARMA"
    const val ACCION_YA_TOME = "co.edu.ucc.hidrasmart.YA_TOME"
    const val ACCION_POSPONER = "co.edu.ucc.hidrasmart.POSPONER"

    private const val CANAL = "alertas_hidratacion"
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
    fun notificar(contexto: Context, alerta: AlertaToma) {
        crearCanal(contexto)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val texto = alerta.ml?.let { "Toma $it ml de agua" } ?: "Toma agua según tu sed"
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_gota)
            .setContentTitle("¡Hora de hidratarte!")
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(PATRON_VIBRACION)
            .setAutoCancel(true)
            .setContentIntent(abrirApp(contexto))
            .setFullScreenIntent(abrirApp(contexto), true)
            .addAction(R.drawable.ic_gota, "Ya tomé", intentReceptor(contexto, ACCION_YA_TOME, 2))
            .addAction(R.drawable.ic_gota, "En 5 min", intentReceptor(contexto, ACCION_POSPONER, 3))
            .build()
        try {
            NotificationManagerCompat.from(contexto).notify(ID_NOTIFICACION, notificacion)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: la alerta se verá al abrir la app
        }
    }

    fun quitarNotificacion(contexto: Context) {
        NotificationManagerCompat.from(contexto).cancel(ID_NOTIFICACION)
    }

    private fun crearCanal(contexto: Context) {
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
        contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    private fun intentReceptor(contexto: Context, accion: String, codigo: Int): PendingIntent =
        PendingIntent.getBroadcast(
            contexto,
            codigo,
            Intent(contexto, AlertaReceiver::class.java).setAction(accion),
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
