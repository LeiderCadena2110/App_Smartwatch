package co.edu.ucc.hidrasmart

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.ucc.hidrasmart.alertas.MotorSesion
import co.edu.ucc.hidrasmart.datos.ClimaServicio
import co.edu.ucc.hidrasmart.datos.LecturaFc
import co.edu.ucc.hidrasmart.datos.Repositorio
import co.edu.ucc.hidrasmart.datos.Sensores
import co.edu.ucc.hidrasmart.dominio.AlertaToma
import co.edu.ucc.hidrasmart.dominio.CalculadoraHidratacion
import co.edu.ucc.hidrasmart.dominio.Clima
import co.edu.ucc.hidrasmart.dominio.Condiciones
import co.edu.ucc.hidrasmart.dominio.Factor
import co.edu.ucc.hidrasmart.dominio.Perfil
import co.edu.ucc.hidrasmart.dominio.PruebaSudoracion
import co.edu.ucc.hidrasmart.dominio.SesionEnCurso
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
    var clima by mutableStateOf<Clima?>(repo.ultimoClima())
        private set
    var mensajeClima by mutableStateOf<String?>(null)
        private set
    var sesion by mutableStateOf<SesionEnCurso?>(repo.sesionActiva())
        private set
    var alerta by mutableStateOf<AlertaToma?>(repo.alertaPendiente())
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
            return tomas.filter { it.instante.aFecha() == hoy }.sumOf { it.ml }
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

    fun guardarPerfil(p: Perfil) {
        repo.guardarPerfil(p)
        perfil = p
        perfilConfigurado = true
        MotorSesion.ajustarIntervalo(getApplication<Application>())
        recargar()
    }

    /** Calcula la tasa con la fórmula NATA y la guarda junto con las condiciones de hoy. */
    fun guardarPrueba(prueba: PruebaSudoracion): Result<Double> = runCatching {
        val tasa = CalculadoraHidratacion.tasaSudoracion(prueba)
        val condiciones = condicionesActuales().copy(fcBpm = null)
        guardarPerfil(perfil.copy(tasaSudoracionLh = tasa, condicionesPrueba = condiciones, pesoKg = prueba.pesoInicialKg))
        tasa
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
                val c = ClimaServicio.consultar(perfil.ciudad, clave)
                repo.guardarClima(c)
                clima = c
                mensajeClima = null
            } catch (e: Exception) {
                mensajeClima = "Sin conexión: se usa el último clima guardado"
            }
        }
    }

    fun condicionesActuales(): Condiciones = Condiciones(
        altitudM = altitudSensor ?: perfil.altitudHabitualM,
        temperaturaC = clima?.temperaturaC ?: perfil.temperaturaManualC,
        humedadPct = clima?.humedadPct ?: perfil.humedadManualPct,
        fcBpm = fc?.bpm,
    )

    fun factorActual(): Factor =
        CalculadoraHidratacion.factor(condicionesActuales(), perfil.condicionesPrueba, perfil.edad)

    fun mlPorAlerta(): Int? = perfil.tasaSudoracionLh?.let {
        CalculadoraHidratacion.mlPorAlerta(it, factorActual().valor, perfil.intervaloMin)
    }

    // ---------------- Tomas de agua ----------------

    fun registrarToma(ml: Int) {
        MotorSesion.registrarToma(getApplication<Application>(), ml)
        recargar()
    }

    // ---------------- Entrenamiento ----------------

    fun iniciarSesion(tipo: String) {
        MotorSesion.iniciar(getApplication<Application>(), tipo)
        recargar() // al instante, sin esperar el aviso de cambios
        actualizarClima()
        encenderSensores()
    }

    fun pausarOReanudar() {
        MotorSesion.pausarOReanudar(getApplication<Application>())
        recargar()
    }

    fun duracionMs(s: SesionEnCurso): Long = ((s.pausadaDesde ?: ahora) - s.inicio - s.pausadoMs).coerceAtLeast(0)

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
                    if (sesion?.pausadaDesde == null) {
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
