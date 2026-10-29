package co.edu.ucc.hidrasmart

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.ucc.hidrasmart.alerts.MotorSesion
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
import co.edu.ucc.hidrasmart.ui.aFecha
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Estado de la pantalla. Los datos viven en el reloj (Repositorio) y las reglas del
 * entrenamiento en MotorSesion, para que la alarma funcione aunque la app esté cerrada.
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
    var clima by mutableStateOf<Weather?>(repo.lastWeather())
        private set
    var mensajeClima by mutableStateOf<String?>(null)
        private set
    var sesion by mutableStateOf<ActiveSession?>(repo.activeSession())
        private set
    var alerta by mutableStateOf<IntakeAlert?>(repo.pendingAlert())
        private set
    var fc by mutableStateOf<HeartRateReading?>(null)
        private set
    var altitudSensor by mutableStateOf<Double?>(null)
        private set
    var ahora by mutableLongStateOf(System.currentTimeMillis())
        private set

    private var trabajosSensores: List<Job> = emptyList()
    private var fcSumaPendiente = 0L
    private var fcLecturasPendientes = 0

    // Cuando la alarma o un botón de la notificación cambian algo, la pantalla se actualiza sola
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in HydrationRepository.READING_KEYS) recargar()
    }

    val totalHoyMl: Int
        get() {
            val hoy = LocalDate.now()
            return intakes.filter { it.timestamp.aFecha() == hoy }.sumOf { it.ml }
        }

    init {
        repo.listen(listener)
        actualizarClima()
        if (sesion != null) encenderSensores()
        // Reloj de la pantalla (cronómetro y cuenta regresiva)
        viewModelScope.launch {
            while (isActive) {
                ahora = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        guardarLecturasPendientes()
        repo.stopListening(listener)
    }

    private fun recargar() {
        profile = repo.profile()
        isProfileConfigured = repo.isProfileConfigured()
        intakes = repo.intakes()
        sessions = repo.sessions()
        clima = repo.lastWeather()
        sesion = repo.activeSession()
        alerta = repo.pendingAlert()
        if (sesion == null && trabajosSensores.isNotEmpty()) apagarSensores()
    }

    // ---------------- Perfil y prueba de sudoración ----------------

    fun saveProfile(p: Profile) {
        repo.saveProfile(p)
        profile = p
        isProfileConfigured = true
        MotorSesion.ajustarIntervalo(getApplication<Application>())
        recargar()
    }

    /** Calcula la tasa con la fórmula NATA y la guarda junto con las condiciones de hoy. */
    fun guardarPrueba(sweatTest: SweatTest): Result<Double> = runCatching {
        val rate = HydrationCalculator.sweatRate(sweatTest)
        val condiciones = condicionesActuales().copy(heartRateBpm = null)
        saveProfile(profile.copy(sweatRateLh = rate, testConditions = condiciones, weightKg = sweatTest.initialWeightKg))
        rate
    }

    // ---------------- Clima y condiciones ----------------

    fun actualizarClima() {
        val key = BuildConfig.OPENWEATHER_API_KEY
        if (key.isBlank()) {
            mensajeClima = "Sin clave de clima: se usan los valores manuales"
            return
        }
        viewModelScope.launch {
            try {
                val c = WeatherService.fetch(profile.city, key)
                repo.saveWeather(c)
                clima = c
                mensajeClima = null
            } catch (e: Exception) {
                mensajeClima = "Sin conexión: se usa el último clima guardado"
            }
        }
    }

    fun condicionesActuales(): Conditions = Conditions(
        altitudeM = altitudSensor ?: profile.usualAltitudeM,
        temperatureC = clima?.temperatureC ?: profile.manualTemperatureC,
        humidityPct = clima?.humidityPct ?: profile.manualHumidityPct,
        heartRateBpm = fc?.bpm,
    )

    fun factorActual(): Factor =
        HydrationCalculator.factor(condicionesActuales(), profile.testConditions, profile.age)

    fun mlPerAlert(): Int? = profile.sweatRateLh?.let {
        HydrationCalculator.mlPerAlert(it, factorActual().value, profile.intervalMin)
    }

    // ---------------- Tomas de agua ----------------

    fun registrarToma(ml: Int) {
        MotorSesion.registrarToma(getApplication<Application>(), ml)
        recargar()
    }

    // ---------------- Entrenamiento ----------------

    fun iniciarSesion(type: String) {
        MotorSesion.iniciar(getApplication<Application>(), type)
        recargar() // al instante, sin esperar el aviso de cambios
        actualizarClima()
        encenderSensores()
    }

    fun pausarOReanudar() {
        MotorSesion.pausarOReanudar(getApplication<Application>())
        recargar()
    }

    fun duracionMs(s: ActiveSession): Long = ((s.pausedSince ?: ahora) - s.startTime - s.pausedMs).coerceAtLeast(0)

    fun yaTome() {
        MotorSesion.yaTome(getApplication<Application>())
        recargar()
    }

    fun posponer() {
        MotorSesion.posponer(getApplication<Application>())
        recargar()
    }

    /** Programa la alarma real en 10 s: da tiempo de salir a la esfera para probarla. */
    fun probarAlarma() {
        guardarLecturasPendientes()
        MotorSesion.programarPrueba(getApplication<Application>())
        recargar()
    }

    /** Termina, guarda en el historial y devuelve el id de la sesión guardada. */
    fun terminarSesion(): Long? {
        guardarLecturasPendientes()
        apagarSensores()
        val id = MotorSesion.terminar(getApplication<Application>())
        recargar()
        return id
    }

    // ---------------- Sensores ----------------

    private fun encenderSensores() {
        if (trabajosSensores.isNotEmpty()) return
        val app = getApplication<Application>()
        trabajosSensores = listOf(
            viewModelScope.launch {
                Sensors.heartRate(app).collect { lectura ->
                    fc = lectura
                    if (sesion?.pausedSince == null) {
                        fcSumaPendiente += lectura.bpm
                        fcLecturasPendientes++
                    }
                }
            },
            viewModelScope.launch {
                Sensors.altitude(app).collect { altitudSensor = it }
            },
            // Cada 30 s se guardan las lecturas: así la alarma calcula con datos recientes
            viewModelScope.launch {
                while (isActive) {
                    delay(30_000)
                    guardarLecturasPendientes()
                }
            },
        )
    }

    private fun apagarSensores() {
        trabajosSensores.forEach { it.cancel() }
        trabajosSensores = emptyList()
        fc = null
    }

    private fun guardarLecturasPendientes() {
        val app = getApplication<Application>()
        repo.saveReadings(fc?.bpm, altitudSensor)
        MotorSesion.sumarFc(app, fcSumaPendiente, fcLecturasPendientes)
        fcSumaPendiente = 0
        fcLecturasPendientes = 0
    }
}
