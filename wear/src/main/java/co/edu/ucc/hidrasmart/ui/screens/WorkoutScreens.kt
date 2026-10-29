package co.edu.ucc.hidrasmart.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.alerts.AlertDiagnostics
import co.edu.ucc.hidrasmart.data.Sensors
import co.edu.ucc.hidrasmart.domain.IntakeAlert
import co.edu.ucc.hidrasmart.ui.RoundButton
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.Note
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.ScreenTitle
import co.edu.ucc.hidrasmart.ui.toFixed
import co.edu.ucc.hidrasmart.ui.formatDuration
import co.edu.ucc.hidrasmart.ui.withThousands
import co.edu.ucc.hidrasmart.ui.theme.AppColors

private val WORKOUT_TYPES = listOf("Correr", "Ciclismo", "Gimnasio", "Otro")

/** Elige el tipo de entrenamiento. Antes de empezar pide el permiso de sensores. */
@Composable
fun WorkoutTypeScreen(vm: AppViewModel, onStarted: () -> Unit) {
    val contexto = LocalContext.current
    var pendingType by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Con o sin permisos se puede entrenar: sin sensor la FC es simulada y sin notificaciones
        // la alerta solo se ve con la app abierta
        pendingType?.let { type ->
            vm.iniciarSesion(type)
            onStarted()
        }
    }

    WearScreen {
        item { ScreenTitle("Entrenar", "¿Qué vas a hacer?") }
        WORKOUT_TYPES.forEach { type ->
            item {
                ActionChip(type, AppColors.Blue, {
                    if (Sensors.hasPermissions(contexto)) {
                        vm.iniciarSesion(type)
                        onStarted()
                    } else {
                        pendingType = type
                        permissionLauncher.launch(Sensors.workoutPermissions())
                    }
                })
            }
        }
        if (AlertDiagnostics.allGood(contexto)) {
            item { Note("Las alertas suenan y vibran aunque cierres la app o se apague la pantalla.") }
        } else {
            item { Note("⚠ Faltan permisos de alertas: revisa Perfil → Diagnóstico de alertas.", AppColors.Amber) }
        }
        if (vm.profile.sweatRateLh == null) {
            item { Note("Sin prueba de sudoración las alertas solo te recordarán tomar agua según tu sed.") }
        }
    }
}

@Composable
fun SessionScreen(
    vm: AppViewModel,
    onLogIntake: () -> Unit,
    end: () -> Unit,
    onExit: () -> Unit,
    onOpenAlert: () -> Unit,
) {
    val s = vm.session
    if (s == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }
    val paused = s.pausedSince != null
    val remainingMs = (s.nextAlertAt - vm.now).coerceAtLeast(0)
    val ml = vm.mlPerAlert()
    val conditions = vm.condicionesActuales()
    val reading = vm.fc

    WearScreen {
        item { Note(if (paused) "${s.type} · EN PAUSA" else s.type, if (paused) AppColors.Amber else Color.LightGray) }
        item { Text(formatDuration(vm.duracionMs(s)), style = MaterialTheme.typography.display3) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = "Frecuencia cardíaca", tint = AppColors.Red)
                Text(
                    if (reading != null) "${reading.bpm} lpm${if (reading.simulated) " (simulada)" else ""}" else "-- lpm",
                    style = MaterialTheme.typography.body1,
                )
            }
        }
        item { Text("Tomado: ${s.drankMl.withThousands()} ml", style = MaterialTheme.typography.body2) }
        item {
            Note(
                if (paused) "Alertas en pausa"
                else if (ml != null) "Próxima toma en ${formatDuration(remainingMs)} · ~$ml ml"
                else "Recordatorio en ${formatDuration(remainingMs)}",
                Color.White,
            )
        }
        item {
            Note(
                "${conditions.temperatureC?.toFixed() ?: "--"} °C · ${conditions.humidityPct?.toFixed(0) ?: "--"} % · " +
                    "${conditions.altitudeM?.toFixed(0) ?: "--"} m",
            )
        }
        // Si la alerta se cerró deslizando, queda pendiente aquí
        if (vm.alert != null) {
            item { ActionChip("Responder alerta", AppColors.Green, onOpenAlert) }
        }
        item { ActionChip("Registrar toma", AppColors.Water, onLogIntake, icon = Icons.Default.Add) }
        item {
            ActionChip(if (paused) "Reanudar" else "Pausar", if (paused) AppColors.Blue else AppColors.Amber, vm::togglePause)
        }
        item { ActionChip("Terminar", AppColors.Red, end) }
        if (!paused && vm.alert == null) {
            item {
                ActionChip(
                    "Probar alarma (10 s)", AppColors.Gray, vm::probarAlarma,
                    detail = "Sal a la esfera y espera",
                )
            }
        }
    }
}

/** Alerta dentro de la app (cuando la app está abierta). */
@Composable
fun AlertScreen(vm: AppViewModel, onClose: () -> Unit) {
    val alert = vm.alert
    if (alert == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    AlertContent(
        alert = alert,
        onDrank = {
            vm.markDrank()
            onClose()
        },
        onSnooze = {
            vm.snooze()
            onClose()
        },
    )
}

/** Contenido de la alerta; lo usan la app y la pantalla que sale sobre la esfera. */
@Composable
fun AlertContent(alert: IntakeAlert, onDrank: () -> Unit, onSnooze: () -> Unit) {
    WearScreen {
        item { ScreenTitle("¡Hora de hidratarte!") }
        item {
            Text(
                if (alert.ml != null) "${alert.ml} ml" else "Toma según tu sed",
                style = if (alert.ml != null) MaterialTheme.typography.display2 else MaterialTheme.typography.title2,
                color = AppColors.LightWater,
            )
        }
        if (alert.factor.reasons.isNotEmpty()) {
            item { Note("Ajuste ×${alert.factor.value.toFixed()}: ${alert.factor.reasons.joinToString(", ")}") }
        }
        if (alert.ml == null) {
            item { Note("Haz la prueba de sudoración en Perfil para recibir una cantidad exacta.") }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton("✓", AppColors.Green, onDrank, buttonSize = ButtonDefaults.DefaultButtonSize, description = "Ya tomé")
                RoundButton("5'", AppColors.Gray, onSnooze, buttonSize = ButtonDefaults.DefaultButtonSize, description = "En 5 minutos")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Note("Ya tomé")
                Note("En 5 min")
            }
        }
    }
}

@Composable
fun ConfirmEndScreen(end: () -> Unit, cancel: () -> Unit) {
    WearScreen {
        item { ScreenTitle("¿Terminar el entrenamiento?") }
        item { ActionChip("Sí, terminar", AppColors.Red, end) }
        item { ActionChip("Seguir entrenando", AppColors.Gray, cancel) }
    }
}

@Composable
fun SummaryScreen(vm: AppViewModel, id: Long, onDone: () -> Unit) {
    val s = vm.sessions.firstOrNull { it.id == id }
    WearScreen {
        item { ScreenTitle("Resumen", s?.type) }
        if (s != null) {
            item { Text(formatDuration(s.durationSec * 1000), style = MaterialTheme.typography.display3) }
            item { Note("FC media: ${s.avgHeartRate?.let { "$it lpm" } ?: "sin datos"}", Color.White) }
            item {
                Note(
                    if (s.recommendedMl > 0) "Tomado ${s.drankMl.withThousands()} ml de ${s.recommendedMl.withThousands()} ml recomendados"
                    else "Tomado ${s.drankMl.withThousands()} ml",
                    Color.White,
                )
            }
            item { Note("Guardado en el historial") }
        }
        item { ActionChip("Listo", AppColors.Green, onDone) }
    }
}

