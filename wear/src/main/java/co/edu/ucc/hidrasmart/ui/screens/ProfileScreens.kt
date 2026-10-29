package co.edu.ucc.hidrasmart.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.Manifest
import android.os.Build
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.BuildConfig
import co.edu.ucc.hidrasmart.alerts.AlertDiagnostics
import co.edu.ucc.hidrasmart.data.Sensors
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.NumberStepper
import co.edu.ucc.hidrasmart.ui.Note
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.ScreenTitle
import co.edu.ucc.hidrasmart.ui.toFixed
import co.edu.ucc.hidrasmart.ui.withThousands
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlin.math.roundToInt

/** Campos numéricos del perfil que se editan con − y +. */
enum class CampoPerfil(
    val titulo: String,
    val unidad: String,
    val paso: Double,
    val minimo: Double,
    val maximo: Double,
    val digits: Int,
) {
    PESO("Peso", "kg", 0.5, 30.0, 200.0, 1),
    EDAD("Edad", "años", 1.0, 10.0, 90.0, 0),
    META("Meta diaria", "ml", 100.0, 500.0, 6000.0, 0),
    INTERVALO("Intervalo de alertas", "min", 5.0, 1.0, 60.0, 0), // 1 min solo para pruebas
    ALTITUD("Altitud habitual", "m", 50.0, 0.0, 5000.0, 0),
    TEMPERATURA("Temperatura manual", "°C", 1.0, -10.0, 45.0, 0),
    HUMEDAD("Humedad manual", "%", 5.0, 0.0, 100.0, 0);

    fun leer(p: Profile): Double = when (this) {
        PESO -> p.weightKg
        EDAD -> p.age.toDouble()
        META -> p.dailyGoalMl.toDouble()
        INTERVALO -> p.intervalMin.toDouble()
        ALTITUD -> p.usualAltitudeM
        TEMPERATURA -> p.manualTemperatureC
        HUMEDAD -> p.manualHumidityPct
    }

    fun escribir(p: Profile, v: Double): Profile = when (this) {
        PESO -> p.copy(weightKg = v)
        EDAD -> p.copy(age = v.roundToInt())
        META -> p.copy(dailyGoalMl = v.roundToInt())
        INTERVALO -> p.copy(intervalMin = v.roundToInt())
        ALTITUD -> p.copy(usualAltitudeM = v)
        TEMPERATURA -> p.copy(manualTemperatureC = v)
        HUMEDAD -> p.copy(manualHumidityPct = v)
    }

    fun text(p: Profile): String = "${leer(p).toFixed(digits)} $unidad"
}

private val CIUDADES = listOf("Pasto,CO", "Ipiales,CO", "Tumaco,CO", "Bogota,CO", "Medellin,CO", "Cali,CO")

@Composable
fun PantallaPerfil(vm: AppViewModel, editar: (CampoPerfil) -> Unit, abrirPrueba: () -> Unit) {
    val p = vm.profile
    val contexto = LocalContext.current
    var permiso by remember { mutableStateOf(Sensors.hasPermissions(contexto)) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permiso = Sensors.hasPermissions(contexto)
    }

    // Diagnóstico de alertas: se vuelve a revisar cada vez que se regresa a esta pantalla
    var notificationsEnabled by remember { mutableStateOf(AlertDiagnostics.notificationsEnabled(contexto)) }
    var exactas by remember { mutableStateOf(AlertDiagnostics.exactAlarmsAllowed(contexto)) }
    var completa by remember { mutableStateOf(AlertDiagnostics.fullScreenAllowed(contexto)) }
    fun revisar() {
        permiso = Sensors.hasPermissions(contexto)
        notificationsEnabled = AlertDiagnostics.notificationsEnabled(contexto)
        exactas = AlertDiagnostics.exactAlarmsAllowed(contexto)
        completa = AlertDiagnostics.fullScreenAllowed(contexto)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revisar() }
    var yaPidioNotificaciones by rememberSaveable { mutableStateOf(false) }
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revisar()
        // Si el sistema ya no muestra el diálogo, se abren los ajustes de la app
        if (!notificationsEnabled) AlertDiagnostics.openNotificationSettings(contexto)
    }

    WearScreen {
        item { ScreenTitle("Perfil y ajustes") }
        if (!(notificationsEnabled && exactas && completa)) {
            item { Note("⚠ Las alertas no saldrán con la app cerrada: baja a \"Diagnóstico de alertas\".", AppColors.Amber) }
        }
        item {
            ActionChip(
                "Prueba de sudoración", AppColors.Water, abrirPrueba,
                detail = p.sweatRateLh?.let { "${it.toFixed(2)} L/h" } ?: "Sin hacer",
            )
        }
        listOf(CampoPerfil.PESO, CampoPerfil.EDAD, CampoPerfil.META, CampoPerfil.INTERVALO).forEach { campo ->
            item { ActionChip(campo.titulo, AppColors.DarkGray, { editar(campo) }, detail = campo.text(p)) }
        }
        item {
            ActionChip("Ciudad del clima", AppColors.DarkGray, {
                val next = CIUDADES[(CIUDADES.indexOf(p.city) + 1) % CIUDADES.size]
                vm.saveProfile(p.copy(city = next))
                vm.actualizarClima()
            }, detail = p.city.substringBefore(","))
        }
        val clima = vm.clima
        item {
            Note(
                if (clima != null) "Clima: ${clima.temperatureC.toFixed()} °C · ${clima.humidityPct.toFixed(0)} %"
                else vm.mensajeClima ?: "Clima sin consultar",
            )
        }
        listOf(CampoPerfil.ALTITUD, CampoPerfil.TEMPERATURA, CampoPerfil.HUMEDAD).forEach { campo ->
            item { ActionChip(campo.titulo, AppColors.DarkGray, { editar(campo) }, detail = campo.text(p)) }
        }
        item {
            ActionChip(
                "Permisos (sensores y alertas)", if (permiso) AppColors.Green else AppColors.Amber,
                { if (!permiso) pedirPermiso.launch(Sensors.workoutPermissions()) },
                detail = if (permiso) "Concedido" else "Toca para permitir",
            )
        }
        item { Note("Altitud, temperatura y humedad manuales se usan cuando no hay barómetro o internet.") }

        item { ScreenTitle("Diagnóstico de alertas", "Para que salgan con la app cerrada") }
        item {
            ActionChip(
                "Notificaciones", if (notificationsEnabled) AppColors.Green else AppColors.Red,
                {
                    if (!notificationsEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !yaPidioNotificaciones) {
                            yaPidioNotificaciones = true
                            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AlertDiagnostics.openNotificationSettings(contexto)
                        }
                    }
                },
                detail = if (notificationsEnabled) "Activadas" else "BLOQUEADAS · toca para activar",
            )
        }
        item {
            ActionChip(
                "Alarmas exactas", if (exactas) AppColors.Green else AppColors.Red,
                { if (!exactas) AlertDiagnostics.openAlarmSettings(contexto) },
                detail = if (exactas) "Permitidas" else "Toca para permitir",
            )
        }
        item {
            ActionChip(
                "Pantalla completa", if (completa) AppColors.Green else AppColors.Red,
                { if (!completa) AlertDiagnostics.openFullScreenSettings(contexto) },
                detail = if (completa) "Permitida" else "Toca para permitir",
            )
        }
        item { Note("Versión ${BuildConfig.VERSION_NAME}") }
    }
}

@Composable
fun PantallaEditarCampo(vm: AppViewModel, campo: CampoPerfil, listo: () -> Unit) {
    var value by rememberSaveable { mutableDoubleStateOf(campo.leer(vm.profile)) }
    fun ajustar(delta: Double) {
        val nuevo = ((value + delta) * 10).roundToInt() / 10.0
        value = nuevo.coerceIn(campo.minimo, campo.maximo)
    }
    WearScreen {
        item { ScreenTitle(campo.titulo) }
        item { NumberStepper(campo.unidad, value.toFixed(campo.digits), { ajustar(-campo.paso) }, { ajustar(campo.paso) }) }
        item {
            ActionChip("Guardar", AppColors.Green, {
                vm.saveProfile(campo.escribir(vm.profile, value))
                listo()
            })
        }
    }
}

/**
 * Prueba de sudoración (NATA 2017): pesarse antes y después de entrenar 1 hora,
 * anotar lo que se tomó y la orina. El reloj no pesa: los datos se ingresan aquí.
 */
@Composable
fun PantallaPrueba(vm: AppViewModel, listo: () -> Unit) {
    var pesoInicial by rememberSaveable { mutableDoubleStateOf(vm.profile.weightKg) }
    var pesoFinal by rememberSaveable { mutableDoubleStateOf(vm.profile.weightKg - 0.5) }
    var liquido by rememberSaveable { mutableIntStateOf(0) }
    var orina by rememberSaveable { mutableIntStateOf(0) }
    var minutos by rememberSaveable { mutableIntStateOf(60) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun paso(v: Double, d: Double) = (((v + d) * 10).roundToInt() / 10.0).coerceIn(30.0, 200.0)

    WearScreen {
        item { ScreenTitle("Prueba de sudoración", "Pésate antes y después de entrenar") }
        item { NumberStepper("Peso inicial (kg)", pesoInicial.toFixed(1), { pesoInicial = paso(pesoInicial, -0.1) }, { pesoInicial = paso(pesoInicial, 0.1) }) }
        item { NumberStepper("Peso final (kg)", pesoFinal.toFixed(1), { pesoFinal = paso(pesoFinal, -0.1) }, { pesoFinal = paso(pesoFinal, 0.1) }) }
        item { NumberStepper("Líquido tomado (ml)", liquido.withThousands(), { liquido = (liquido - 50).coerceAtLeast(0) }, { liquido += 50 }) }
        item { NumberStepper("Orina (ml)", orina.withThousands(), { orina = (orina - 50).coerceAtLeast(0) }, { orina += 50 }) }
        item { NumberStepper("Duración (min)", "$minutos", { minutos = (minutos - 5).coerceAtLeast(5) }, { minutos = (minutos + 5).coerceAtMost(240) }) }

        val mensajeActual = mensaje
        if (mensajeActual != null) {
            item { Note(mensajeActual, if (saved) AppColors.LightWater else AppColors.Amber) }
        }
        item {
            if (!saved) {
                ActionChip("Calcular y guardar", AppColors.Green, {
                    val sweatTest = SweatTest(pesoInicial, pesoFinal, liquido, orina, minutos)
                    vm.guardarPrueba(sweatTest)
                        .onSuccess { rate ->
                            val perdida = HydrationCalculator.weightLossPercent(sweatTest)
                            saved = true
                            mensaje = "Tasa: ${rate.toFixed(2)} L/h. Perdiste ${perdida.toFixed(1)} % del peso" +
                                if (perdida >= 2.0) " (NATA recomienda menos del 2 %)" else ""
                        }
                        .onFailure { mensaje = it.message ?: "Revisa los datos" }
                })
            } else {
                ActionChip("Listo", AppColors.Green, listo)
            }
        }
    }
}
