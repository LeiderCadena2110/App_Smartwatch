package co.edu.ucc.hidrasmart.alerts

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.edu.ucc.hidrasmart.data.HydrationRepository
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.ui.screens.ContenidoAlerta
import co.edu.ucc.hidrasmart.ui.theme.HidraTema

/**
 * Pantalla de alerta independiente: se abre sobre la esfera del reloj aunque la app
 * esté cerrada o la pantalla apagada (la lanza la notificación de pantalla completa).
 */
class AlertaActivity : ComponentActivity() {

    private lateinit var repo: HydrationRepository
    private var alerta by mutableStateOf<IntakeAlert?>(null)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in HydrationRepository.READING_KEYS) actualizar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Encender la pantalla y mostrarse encima de la esfera / bloqueo
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        repo = HydrationRepository(this)
        alerta = repo.pendingAlert()
        if (alerta == null) {
            finish()
            return
        }
        setContent {
            HidraTema {
                val current = alerta
                if (current != null) {
                    ContenidoAlerta(
                        alerta = current,
                        onYaTome = {
                            MotorSesion.yaTome(this)
                            finish()
                        },
                        onPosponer = {
                            MotorSesion.posponer(this)
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        repo.listen(listener)
        actualizar()
    }

    override fun onStop() {
        super.onStop()
        visible = false
        repo.stopListening(listener)
    }

    /** Si la alerta se respondió desde la notificación o la app, esta pantalla se cierra. */
    private fun actualizar() {
        alerta = repo.pendingAlert()
        if (alerta == null) finish()
    }

    companion object {
        @Volatile
        var visible = false
    }
}
