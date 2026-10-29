package co.edu.ucc.hidrasmart.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe la alarma y los botones de la notificación, aunque la app esté cerrada. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_ALARM -> {
                SessionEngine.fireAlert(contexto)
                // Además de la notificación de pantalla completa, intentar abrir la alerta directamente
                AlarmScheduler.openAlertScreen(contexto)
            }
            AlarmScheduler.ACTION_DRANK -> SessionEngine.markDrank(contexto)
            AlarmScheduler.ACTION_SNOOZE -> SessionEngine.snooze(contexto)
        }
    }
}
