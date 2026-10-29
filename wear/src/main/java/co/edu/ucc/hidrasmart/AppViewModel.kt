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
import co.edu.ucc.hidrasmart.data.ClimaServicio
import co.edu.ucc.hidrasmart.data.LecturaFc
import co.edu.ucc.hidrasmart.data.Repositorio
import co.edu.ucc.hidrasmart.data.Sensores
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

    private val repo = Repositorio(app)

    var perfil by mutableStateOf(repo.perfil())
        private set
    var perfilConfigurado by mutableStateOf(repo.perfilConfigurado())
        private set
    var tomas by mutableStateOf(repo.tomas())
        private set
    var sesiones by mutableStateOf(repo.sesiones())
        private set
    var clima by mutableStateOf<Weather?>(repo.ultimoClima())
        private set
    var mensajeClima by mutableStateOf<String?>(null)
        private set
    var sesion by mutableStateOf<ActiveSession?>(repo.sesionActiva())
        private set
    var alerta by mutableStateOf<IntakeAlert?>(repo.alertaPendiente())
        private set
    var fc by mutableStateOf<LecturaFc?>(null)
        private set
    var altitudSensor by mutableStateOf<Double?>(null)
        private set
    var ahora by mutableLongStateOf(System.currentTimeMillis())
        private set

    private var trabajosSensores: List<Job> = emptyList()
    private var fcSumaPendiente = 0L
    private var fcLecturasPendientes = 0

    // Cuando la alarma o un botón de la notificación cambian algo, la pantalla se actualiza sola
    private val oyente = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
        if (clave !in Repositorio.CLAVES_LECTURAS) recargar()
    }

    val totalHoyMl: Int
        get() {
            val hoy = LocalDate.now()
            return tomas.filter { it.timestamp.aFecha() == hoy }.sumOf { it.ml }
        }

    init {
        repo.escuchar(oyente)
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
        repo.dejarDeEscuchar(oyente)
    }

    private fun recargar() {
        perfil = repo.perfil()
        perfilConfigurado = repo.perfilConfigurado()
        tomas = repo.tomas()
        sesiones = repo.sesiones()
        clima = repo.ultimoClima()
        sesion = repo.sesionActiva()
        alerta = repo.alertaPendiente()
        if (sesion == null && trabajosSensores.isNotEmpty()) apagarSensores()
    }

    // ---------------- Perfil y prueba de sudoración ----------------

    fun guardarPerfil(p: Profile) {
        repo.guardarPerfil(p)
        perfil = p
        perfilConfigurado = true
        MotorSesion.ajustarIntervalo(getApplication<Application>())
        recargar()
    }

    /** Calcula la tasa con la fórmula NATA y la guarda junto con las condiciones de hoy. */
    fun guardarPrueba(sweatTest: SweatTest): Result<Double> = runCatching {
        val rate = HydrationCalculator.sweatRate(sweatTest)
        val condiciones = condicionesActuales().copy(heartRateBpm = null)
        guardarPerfil(perfil.copy(sweatRateLh = rate, testConditions = condiciones, weightKg = sweatTest.initialWeightKg))
        rate
    }

    // ---------------- Clima y condiciones ----------------

    fun actualizarClima() {
        val clave = BuildConfig.OPENWEATHER_API_KEY
        if (clave.isBlank()) {
            mensajeClima = "Sin clave de clima: se usan los valores manuales"
            return
        }
        viewModelScope.launch {
            try {
                val c = ClimaServicio.consultar(perfil.city, clave)
                repo.guardarClima(c)
                clima = c
                mensajeClima = null
            } catch (e: Exception) {
                mensajeClima = "Sin conexión: se usa el último clima guardado"
            }
        }
    }

    fun condicionesActuales(): Conditions = Conditions(
        altitudeM = altitudSensor ?: perfil.usualAltitudeM,
        temperatureC = clima?.temperatureC ?: perfil.manualTemperatureC,
        humidityPct = clima?.humidityPct ?: perfil.manualHumidityPct,
        heartRateBpm = fc?.bpm,
    )

    fun factorActual(): Factor =
        HydrationCalculator.factor(condicionesActuales(), perfil.testConditions, perfil.age)

    fun mlPerAlert(): Int? = perfil.sweatRateLh?.let {
        HydrationCalculator.mlPerAlert(it, factorActual().value, perfil.intervalMin)
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
                Sensores.ritmoCardiaco(app).collect { lectura ->
                    fc = lectura
                    if (sesion?.pausedSince == null) {
                        fcSumaPendiente += lectura.bpm
                        fcLecturasPendientes++
                    }
                }
            },
            viewModelScope.launch {
                Sensores.altitud(app).collect { altitudSensor = it }
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
        repo.guardarLecturas(fc?.bpm, altitudSensor)
        MotorSesion.sumarFc(app, fcSumaPendiente, fcLecturasPendientes)
        fcSumaPendiente = 0
        fcLecturasPendientes = 0
    }
}
