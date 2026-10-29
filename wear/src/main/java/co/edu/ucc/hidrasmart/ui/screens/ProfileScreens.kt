package co.edu.ucc.hidrasmart.ui.screens

import android.Manifest
import android.os.Build
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
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.BuildConfig
import co.edu.ucc.hidrasmart.alerts.AlertDiagnostics
import co.edu.ucc.hidrasmart.data.Sensors
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.format.toFixed
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.ui.components.ActionChip
import co.edu.ucc.hidrasmart.ui.components.Note
import co.edu.ucc.hidrasmart.ui.components.NumberStepper
import co.edu.ucc.hidrasmart.ui.components.ScreenTitle
import co.edu.ucc.hidrasmart.ui.components.WearScreen
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlin.math.roundToInt

/** Campos numéricos del perfil que se editan con − y +. */
enum class ProfileField(
    val title: String,
    val unitLabel: String,
    val step: Double,
    val minValue: Double,
    val maxValue: Double,
    val digits: Int,
) {
    WEIGHT("Peso", "kg", 0.5, 30.0, 200.0, 1),
    AGE("Edad", "años", 1.0, 10.0, 90.0, 0),
    GOAL("Meta diaria", "ml", 100.0, 500.0, 6000.0, 0),
    INTERVAL("Intervalo de alertas", "min", 5.0, 1.0, 60.0, 0), // 1 min solo para pruebas
    ALTITUDE("Altitud habitual", "m", 50.0, 0.0, 5000.0, 0),
    TEMPERATURE("Temperatura manual", "°C", 1.0, -10.0, 45.0, 0),
    HUMIDITY("Humedad manual", "%", 5.0, 0.0, 100.0, 0);

    fun read(p: Profile): Double = when (this) {
        WEIGHT -> p.weightKg
        AGE -> p.age.toDouble()
        GOAL -> p.dailyGoalMl.toDouble()
        INTERVAL -> p.intervalMin.toDouble()
        ALTITUDE -> p.usualAltitudeM
        TEMPERATURE -> p.manualTemperatureC
        HUMIDITY -> p.manualHumidityPct
    }

    fun write(p: Profile, v: Double): Profile = when (this) {
        WEIGHT -> p.copy(weightKg = v)
        AGE -> p.copy(age = v.roundToInt())
        GOAL -> p.copy(dailyGoalMl = v.roundToInt())
        INTERVAL -> p.copy(intervalMin = v.roundToInt())
        ALTITUDE -> p.copy(usualAltitudeM = v)
        TEMPERATURE -> p.copy(manualTemperatureC = v)
        HUMIDITY -> p.copy(manualHumidityPct = v)
    }

    fun text(p: Profile): String = "${read(p).toFixed(digits)} $unitLabel"
}

private val CITIES = listOf("Pasto,CO", "Ipiales,CO", "Tumaco,CO", "Bogota,CO", "Medellin,CO", "Cali,CO")

@Composable
fun ProfileScreen(vm: AppViewModel, editField: (ProfileField) -> Unit, onOpenSweatTest: () -> Unit) {
    val p = vm.profile
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(Sensors.hasPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionGranted = Sensors.hasPermissions(context)
    }

    // Diagnóstico de alertas: se vuelve a revisar cada vez que se regresa a esta pantalla
    var notificationsEnabled by remember { mutableStateOf(AlertDiagnostics.notificationsEnabled(context)) }
    var exactAllowed by remember { mutableStateOf(AlertDiagnostics.exactAlarmsAllowed(context)) }
    var fullScreenOk by remember { mutableStateOf(AlertDiagnostics.fullScreenAllowed(context)) }
    fun recheck() {
        permissionGranted = Sensors.hasPermissions(context)
        notificationsEnabled = AlertDiagnostics.notificationsEnabled(context)
        exactAllowed = AlertDiagnostics.exactAlarmsAllowed(context)
        fullScreenOk = AlertDiagnostics.fullScreenAllowed(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck() }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        recheck()
        // Si el sistema ya no muestra el diálogo, se abren los ajustes de la app
        if (!notificationsEnabled) AlertDiagnostics.openNotificationSettings(context)
    }

    WearScreen {
        item { ScreenTitle("Perfil y ajustes") }
        if (!(notificationsEnabled && exactAllowed && fullScreenOk)) {
            item { Note("⚠ Las alertas no saldrán con la app cerrada: baja a \"Diagnóstico de alertas\".", AppColors.Amber) }
        }
        item {
            ActionChip(
                "Prueba de sudoración", AppColors.Water, onOpenSweatTest,
                detail = p.sweatRateLh?.let { "${it.toFixed(2)} L/h" } ?: "Sin hacer",
            )
        }
        listOf(ProfileField.WEIGHT, ProfileField.AGE, ProfileField.GOAL, ProfileField.INTERVAL).forEach { field ->
            item { ActionChip(field.title, AppColors.DarkGray, { editField(field) }, detail = field.text(p)) }
        }
        item {
            ActionChip("Ciudad del clima", AppColors.DarkGray, {
                val next = CITIES[(CITIES.indexOf(p.city) + 1) % CITIES.size]
                vm.saveProfile(p.copy(city = next))
                vm.refreshWeather()
            }, detail = p.city.substringBefore(","))
        }
        val weather = vm.weather
        item {
            Note(
                if (weather != null) "Clima: ${weather.temperatureC.toFixed()} °C · ${weather.humidityPct.toFixed(0)} %"
                else vm.weatherMessage ?: "Clima sin consultar",
            )
        }
        listOf(ProfileField.ALTITUDE, ProfileField.TEMPERATURE, ProfileField.HUMIDITY).forEach { field ->
            item { ActionChip(field.title, AppColors.DarkGray, { editField(field) }, detail = field.text(p)) }
        }
        item {
            ActionChip(
                "Permisos (sensores y alertas)", if (permissionGranted) AppColors.Green else AppColors.Amber,
                { if (!permissionGranted) permissionLauncher.launch(Sensors.workoutPermissions()) },
                detail = if (permissionGranted) "Concedido" else "Toca para permitir",
            )
        }
        item { Note("Altitud, temperatura y humedad manuales se usan cuando no hay barómetro o internet.") }

        item { ScreenTitle("Diagnóstico de alertas", "Para que salgan con la app cerrada") }
        item {
            ActionChip(
                "Notificaciones", if (notificationsEnabled) AppColors.Green else AppColors.Red,
                {
                    if (!notificationsEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
                            askedNotifications = true
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AlertDiagnostics.openNotificationSettings(context)
                        }
                    }
                },
                detail = if (notificationsEnabled) "Activadas" else "BLOQUEADAS · toca para activar",
            )
        }
        item {
            ActionChip(
                "Alarmas exactas", if (exactAllowed) AppColors.Green else AppColors.Red,
                { if (!exactAllowed) AlertDiagnostics.openAlarmSettings(context) },
                detail = if (exactAllowed) "Permitidas" else "Toca para permitir",
            )
        }
        item {
            ActionChip(
                "Pantalla completa", if (fullScreenOk) AppColors.Green else AppColors.Red,
                { if (!fullScreenOk) AlertDiagnostics.openFullScreenSettings(context) },
                detail = if (fullScreenOk) "Permitida" else "Toca para permitir",
            )
        }
        item { Note("Versión ${BuildConfig.VERSION_NAME}") }
    }
}

@Composable
fun EditFieldScreen(vm: AppViewModel, field: ProfileField, onDone: () -> Unit) {
    var value by rememberSaveable { mutableDoubleStateOf(field.read(vm.profile)) }
    fun adjust(delta: Double) {
        val newValue = ((value + delta) * 10).roundToInt() / 10.0
        value = newValue.coerceIn(field.minValue, field.maxValue)
    }
    WearScreen {
        item { ScreenTitle(field.title) }
        item { NumberStepper(field.unitLabel, value.toFixed(field.digits), { adjust(-field.step) }, { adjust(field.step) }) }
        item {
            ActionChip("Guardar", AppColors.Green, {
                vm.saveProfile(field.write(vm.profile, value))
                onDone()
            })
        }
    }
}

/**
 * Prueba de sudoración (NATA 2017): pesarse antes y después de entrenar 1 hora,
 * anotar lo que se tomó y la orina. El reloj no pesa: los datos se ingresan aquí.
 */
@Composable
fun SweatTestScreen(vm: AppViewModel, onDone: () -> Unit) {
    var initialWeight by rememberSaveable { mutableDoubleStateOf(vm.profile.weightKg) }
    var finalWeight by rememberSaveable { mutableDoubleStateOf(vm.profile.weightKg - 0.5) }
    var fluid by rememberSaveable { mutableIntStateOf(0) }
    var urine by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun step(v: Double, d: Double) = (((v + d) * 10).roundToInt() / 10.0).coerceIn(30.0, 200.0)

    WearScreen {
        item { ScreenTitle("Prueba de sudoración", "Pésate antes y después de entrenar") }
        item { NumberStepper("Peso inicial (kg)", initialWeight.toFixed(1), { initialWeight = step(initialWeight, -0.1) }, { initialWeight = step(initialWeight, 0.1) }) }
        item { NumberStepper("Peso final (kg)", finalWeight.toFixed(1), { finalWeight = step(finalWeight, -0.1) }, { finalWeight = step(finalWeight, 0.1) }) }
        item { NumberStepper("Líquido tomado (ml)", fluid.withThousands(), { fluid = (fluid - 50).coerceAtLeast(0) }, { fluid += 50 }) }
        item { NumberStepper("Orina (ml)", urine.withThousands(), { urine = (urine - 50).coerceAtLeast(0) }, { urine += 50 }) }
        item { NumberStepper("Duración (min)", "$minutes", { minutes = (minutes - 5).coerceAtLeast(5) }, { minutes = (minutes + 5).coerceAtMost(240) }) }

        val currentMessage = message
        if (currentMessage != null) {
            item { Note(currentMessage, if (saved) AppColors.LightWater else AppColors.Amber) }
        }
        item {
            if (!saved) {
                ActionChip("Calcular y guardar", AppColors.Green, {
                    val sweatTest = SweatTest(initialWeight, finalWeight, fluid, urine, minutes)
                    vm.saveSweatTest(sweatTest)
                        .onSuccess { rate ->
                            val loss = HydrationCalculator.weightLossPercent(sweatTest)
                            saved = true
                            message = "Tasa: ${rate.toFixed(2)} L/h. Perdiste ${loss.toFixed(1)} % del peso" +
                                if (loss >= 2.0) " (NATA recomienda menos del 2 %)" else ""
                        }
                        .onFailure { message = it.message ?: "Revisa los datos" }
                })
            } else {
                ActionChip("Listo", AppColors.Green, onDone)
            }
        }
    }
}
