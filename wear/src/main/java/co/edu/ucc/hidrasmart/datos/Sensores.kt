package co.edu.ucc.hidrasmart.datos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Lectura de frecuencia cardíaca. [simulada] = true cuando no hay sensor (por ejemplo, en el emulador). */
data class LecturaFc(val bpm: Int, val simulada: Boolean)

object Sensores {

    const val PERMISO_FC = Manifest.permission.BODY_SENSORS

    /** Permisos que se piden al empezar a entrenar: sensores y notificaciones (Android 13+). */
    fun permisosEntrenamiento(): Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(PERMISO_FC, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(PERMISO_FC)
        }

    fun tienePermisos(contexto: Context): Boolean = permisosEntrenamiento().all {
        ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
    }

    fun tienePermisoFc(contexto: Context): Boolean =
        ContextCompat.checkSelfPermission(contexto, PERMISO_FC) == PackageManager.PERMISSION_GRANTED

    /** Frecuencia cardíaca en tiempo real. Si no hay sensor o permiso, genera valores de demostración. */
    fun ritmoCardiaco(contexto: Context): Flow<LecturaFc> = callbackFlow {
        val gestor = contexto.getSystemService(SensorManager::class.java)
        val sensor = gestor?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (gestor == null || sensor == null || !tienePermisoFc(contexto)) {
            val simulador = launch {
                var bpm = 110
                while (isActive) {
                    bpm = (bpm + Random.nextInt(-5, 6)).coerceIn(95, 170)
                    trySend(LecturaFc(bpm, simulada = true))
                    delay(2000)
                }
            }
            awaitClose { simulador.cancel() }
        } else {
            val oyente = object : SensorEventListener {
                override fun onSensorChanged(evento: SensorEvent) {
                    val bpm = evento.values.firstOrNull()?.toInt() ?: 0
                    if (bpm > 0) trySend(LecturaFc(bpm, simulada = false))
                }

                override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
            }
            gestor.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { gestor.unregisterListener(oyente) }
        }
    }

    /** Altitud estimada con el barómetro. Si el reloj no tiene barómetro, no emite nada. */
    fun altitud(contexto: Context): Flow<Double> = callbackFlow {
        val gestor = contexto.getSystemService(SensorManager::class.java)
        val sensor = gestor?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (gestor == null || sensor == null) {
            close()
            awaitClose { }
        } else {
            val oyente = object : SensorEventListener {
                override fun onSensorChanged(evento: SensorEvent) {
                    val presion = evento.values.firstOrNull() ?: return
                    trySend(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, presion).toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
            }
            gestor.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { gestor.unregisterListener(oyente) }
        }
    }
}
