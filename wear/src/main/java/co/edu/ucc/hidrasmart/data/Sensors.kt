package co.edu.ucc.hidrasmart.data

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
data class HeartRateReading(val bpm: Int, val simulated: Boolean)

object Sensors {

    const val HEART_RATE_PERMISSION = Manifest.permission.BODY_SENSORS

    /** Permisos que se piden al empezar a entrenar: sensores y notificaciones (Android 13+). */
    fun workoutPermissions(): Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(HEART_RATE_PERMISSION, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(HEART_RATE_PERMISSION)
        }

    fun hasPermissions(context: Context): Boolean = workoutPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasHeartRatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, HEART_RATE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Frecuencia cardíaca en tiempo real. Si no hay sensor o permiso, genera valores de demostración. */
    fun heartRate(context: Context): Flow<HeartRateReading> = callbackFlow {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (sensorManager == null || sensor == null || !hasHeartRatePermission(context)) {
            val simulator = launch {
                var bpm = 110
                while (isActive) {
                    bpm = (bpm + Random.nextInt(-5, 6)).coerceIn(95, 170)
                    trySend(HeartRateReading(bpm, simulated = true))
                    delay(2000)
                }
            }
            awaitClose { simulator.cancel() }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val bpm = event.values.firstOrNull()?.toInt() ?: 0
                    if (bpm > 0) trySend(HeartRateReading(bpm, simulated = false))
                }

                override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    /** Altitud estimada con el barómetro. Si el reloj no tiene barómetro, no emite nada. */
    fun altitude(context: Context): Flow<Double> = callbackFlow {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensorManager == null || sensor == null) {
            close()
            awaitClose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val pressure = event.values.firstOrNull() ?: return
                    trySend(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure).toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }
}
