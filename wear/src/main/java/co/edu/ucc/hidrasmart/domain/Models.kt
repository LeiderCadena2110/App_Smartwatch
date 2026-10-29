package co.edu.ucc.hidrasmart.domain

/** Condiciones en las que se entrena (o en las que se hizo la prueba de sudoración). */
data class Conditions(
    val altitudeM: Double? = null,
    val temperatureC: Double? = null,
    val humidityPct: Double? = null,
    val heartRateBpm: Int? = null,
)

/** Datos que el atleta ingresa después de la prueba de sudoración. */
data class SweatTest(
    val initialWeightKg: Double,
    val finalWeightKg: Double,
    val fluidMl: Int,
    val urineMl: Int,
    val durationMin: Int,
)

/**
 * Perfil del atleta. Los valores por defecto son solo un punto de partida:
 * el atleta los cambia en Perfil y ajustes.
 */
data class Profile(
    val weightKg: Double = 70.0,
    val age: Int = 22,
    val dailyGoalMl: Int = 2000,
    val intervalMin: Int = 15,
    val city: String = "Pasto,CO",
    val usualAltitudeM: Double = 2527.0,
    val manualTemperatureC: Double = 14.0,
    val manualHumidityPct: Double = 75.0,
    val sweatRateLh: Double? = null,
    val testConditions: Conditions? = null,
)

data class Intake(val timestamp: Long, val ml: Int)

data class SavedSession(
    val id: Long,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Long,
    val avgHeartRate: Int?,
    val drankMl: Int,
    val recommendedMl: Int,
)

data class Weather(val temperatureC: Double, val humidityPct: Double, val city: String, val timestamp: Long)

/** Factor de ajuste y las razones que lo explican (para mostrarlas al atleta). */
data class Factor(val value: Double, val reasons: List<String>)

/** Entrenamiento en curso. Se guarda en el reloj para que las alarmas funcionen con la app cerrada. */
data class ActiveSession(
    val type: String,
    val startTime: Long,
    val nextAlertAt: Long,
    val pausedSince: Long? = null,
    val pausedMs: Long = 0,
    val drankMl: Int = 0,
    val recommendedMl: Int = 0,
    val heartRateSum: Long = 0,
    val heartRateReadings: Int = 0,
)

/** Alerta de hidratación pendiente de respuesta. [ml] es null si aún no hay prueba de sudoración. */
data class IntakeAlert(val ml: Int?, val factor: Factor, val timestamp: Long)
