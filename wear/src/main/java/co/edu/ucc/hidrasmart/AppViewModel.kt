package co.edu.ucc.hidrasmart

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.ucc.hidrasmart.alerts.SessionEngine
import co.edu.ucc.hidrasmart.data.WeatherService
import co.edu.ucc.hidrasmart.data.HeartRateReading
import co.edu.ucc.hidrasmart.data.HydrationRepository
import co.edu.ucc.hidrasmart.data.Sensors
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Weather
import co.edu.ucc.hidrasmart.domain.Conditions
import co.edu.ucc.hidrasmart.domain.Factor
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.domain.ActiveSession
import co.edu.ucc.hidrasmart.format.toLocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Estado de la pantalla. Los datos viven en el reloj (HydrationRepository) y las reglas del
 * entrenamiento en SessionEngine, para que la alarma funcione aunque la app esté cerrada.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HydrationRepository(app)

    var profile by mutableStateOf(repo.profile())
        private set
    var isProfileConfigured by mutableStateOf(repo.isProfileConfigured())
        private set
    var intakes by mutableStateOf(repo.intakes())
        private set
    var sessions by mutableStateOf(repo.sessions())
        private set
    var weather by mutableStateOf<Weather?>(repo.lastWeather())
        private set
    var weatherMessage by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<ActiveSession?>(repo.activeSession())
        private set
    var alert by mutableStateOf<IntakeAlert?>(repo.pendingAlert())
        private set
    var heartRate by mutableStateOf<HeartRateReading?>(null)
        private set
    var sensorAltitude by mutableStateOf<Double?>(null)
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    private var sensorJobs: List<Job> = emptyList()
    private var pendingHeartRateSum = 0L
    private var pendingHeartRateReadings = 0

    // Cuando la alarma o un botón de la notificación cambian algo, la pantalla se actualiza sola
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in HydrationRepository.READING_KEYS) reload()
    }

    val todayTotalMl: Int
        get() {
            val today = LocalDate.now()
            return intakes.filter { it.timestamp.toLocalDate() == today }.sumOf { it.ml }
        }

    init {
        repo.listen(listener)
        refreshWeather()
        if (session != null) startSensors()
        // Reloj de la pantalla (cronómetro y cuenta regresiva)
        viewModelScope.launch {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        flushReadings()
        repo.stopListening(listener)
    }

    private fun reload() {
        profile = repo.profile()
        isProfileConfigured = repo.isProfileConfigured()
        intakes = repo.intakes()
        sessions = repo.sessions()
        weather = repo.lastWeather()
        session = repo.activeSession()
        alert = repo.pendingAlert()
        if (session == null && sensorJobs.isNotEmpty()) stopSensors()
    }

    // ---------------- Perfil y prueba de sudoración ----------------

    fun saveProfile(p: Profile) {
        repo.saveProfile(p)
        profile = p
        isProfileConfigured = true
        SessionEngine.adjustInterval(getApplication<Application>())
        reload()
    }

    /** Calcula la tasa con la fórmula NATA y la guarda junto con las condiciones de hoy. */
    fun saveSweatTest(sweatTest: SweatTest): Result<Double> = runCatching {
        val rate = HydrationCalculator.sweatRate(sweatTest)
        val conditions = currentConditions().copy(heartRateBpm = null)
        saveProfile(profile.copy(sweatRateLh = rate, testConditions = conditions, weightKg = sweatTest.initialWeightKg))
        rate
    }

    // ---------------- Clima y condiciones ----------------

    fun refreshWeather() {
        val key = BuildConfig.OPENWEATHER_API_KEY
        if (key.isBlank()) {
            weatherMessage = "Sin clave de clima: se usan los valores manuales"
            return
        }
        viewModelScope.launch {
            try {
                val c = WeatherService.fetch(profile.city, key)
                repo.saveWeather(c)
                weather = c
                weatherMessage = null
            } catch (e: Exception) {
                weatherMessage = "Sin conexión: se usa el último clima guardado"
            }
        }
    }

    fun currentConditions(): Conditions = Conditions(
        altitudeM = sensorAltitude ?: profile.usualAltitudeM,
        temperatureC = weather?.temperatureC ?: profile.manualTemperatureC,
        humidityPct = weather?.humidityPct ?: profile.manualHumidityPct,
        heartRateBpm = heartRate?.bpm,
    )

    fun currentFactor(): Factor =
        HydrationCalculator.factor(currentConditions(), profile.testConditions, profile.age)

    fun mlPerAlert(): Int? = profile.sweatRateLh?.let {
        HydrationCalculator.mlPerAlert(it, currentFactor().value, profile.intervalMin)
    }

    // ---------------- Tomas de agua ----------------

    fun logIntake(ml: Int) {
        SessionEngine.logIntake(getApplication<Application>(), ml)
        reload()
    }

    // ---------------- Entrenamiento ----------------

    fun startSession(type: String) {
        SessionEngine.start(getApplication<Application>(), type)
        reload() // al instante, sin esperar el aviso de cambios
        refreshWeather()
        startSensors()
    }

    fun togglePause() {
        SessionEngine.togglePause(getApplication<Application>())
        reload()
    }

    fun durationMs(s: ActiveSession): Long = ((s.pausedSince ?: now) - s.startTime - s.pausedMs).coerceAtLeast(0)

    fun markDrank() {
        SessionEngine.markDrank(getApplication<Application>())
        reload()
    }

    fun snooze() {
        SessionEngine.snooze(getApplication<Application>())
        reload()
    }

    /** Programa la alarma real en 10 s: da tiempo de salir a la esfera para probarla. */
    fun testAlarm() {
        flushReadings()
        SessionEngine.scheduleTest(getApplication<Application>())
        reload()
    }

    /** Termina, guarda en el historial y devuelve el id de la sesión guardada. */
    fun endSession(): Long? {
        flushReadings()
        stopSensors()
        val id = SessionEngine.end(getApplication<Application>())
        reload()
        return id
    }

    // ---------------- Sensores ----------------

    private fun startSensors() {
        if (sensorJobs.isNotEmpty()) return
        val app = getApplication<Application>()
        sensorJobs = listOf(
            viewModelScope.launch {
                Sensors.heartRate(app).collect { reading ->
                    heartRate = reading
                    if (session?.pausedSince == null) {
                        pendingHeartRateSum += reading.bpm
                        pendingHeartRateReadings++
                    }
                }
            },
            viewModelScope.launch {
                Sensors.altitude(app).collect { sensorAltitude = it }
            },
            // Cada 30 s se guardan las lecturas: así la alarma calcula con datos recientes
            viewModelScope.launch {
                while (isActive) {
                    delay(30_000)
                    flushReadings()
                }
            },
        )
    }

    private fun stopSensors() {
        sensorJobs.forEach { it.cancel() }
        sensorJobs = emptyList()
        heartRate = null
    }

    private fun flushReadings() {
        val app = getApplication<Application>()
        repo.saveReadings(heartRate?.bpm, sensorAltitude)
        SessionEngine.addHeartRate(app, pendingHeartRateSum, pendingHeartRateReadings)
        pendingHeartRateSum = 0
        pendingHeartRateReadings = 0
    }
}
