package co.edu.ucc.hidrasmart.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe la alarma y los botones de la notificación, aunque la app esté cerrada. */
class AlertaReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        when (intent.action) {
            Alarmas.ACCION_ALARMA -> {
                MotorSesion.dispararAlerta(contexto)
                // Además de la notificación de pantalla completa, intentar abrir la alerta directamente
                Alarmas.abrirPantallaAlerta(contexto)
            }
            Alarmas.ACCION_YA_TOME -> MotorSesion.yaTome(contexto)
            Alarmas.ACCION_POSPONER -> MotorSesion.posponer(contexto)
        }
    }
}
