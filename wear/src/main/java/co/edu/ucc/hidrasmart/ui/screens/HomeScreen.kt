package co.edu.ucc.hidrasmart.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.format.formatDuration
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val total = vm.todayTotalMl
    val goal = vm.profile.dailyGoalMl
    val session = vm.session
    val topInsight = remember(vm.intakes, vm.sessions, vm.profile) { vm.insights().firstOrNull() }

    WearScreen {
        item { ProgressRing(total, goal) }

        if (!vm.isProfileConfigured) {
            item { ActionChip("Configura tu perfil", AppColors.Amber, { onNavigate(Routes.PROFILE) }, icon = Icons.Default.Person) }
        }
        if (session != null) {
            item {
                ActionChip(
                    "Volver a la sesión", AppColors.Blue, { onNavigate(Routes.SESSION) },
                    detail = "${session.type} · ${formatDuration(vm.durationMs(session))}",
                    icon = Icons.Default.PlayArrow,
                )
            }
        }
        item { ActionChip("Registrar toma", AppColors.Water, { onNavigate(Routes.LOG_INTAKE) }, icon = Icons.Default.Add) }
        item {
            ActionChip(
                "Coach IA", AppColors.Ai, { onNavigate(Routes.COACH) },
                detail = topInsight?.title ?: "Consejos personalizados",
                icon = Icons.Default.Star,
            )
        }
        if (session == null) {
            item { ActionChip("Entrenar", AppColors.Blue, { onNavigate(Routes.WORKOUT_TYPE) }, icon = Icons.Default.PlayArrow) }
        }
        item { ActionChip("Historial", AppColors.DarkGray, { onNavigate(Routes.HISTORY) }, icon = Icons.Default.DateRange) }
        item { ActionChip("Perfil y ajustes", AppColors.DarkGray, { onNavigate(Routes.PROFILE) }, icon = Icons.Default.Settings) }
    }
}

/** Anillo con lo tomado hoy frente a la meta diaria. */
@Composable
private fun ProgressRing(total: Int, goal: Int) {
    val progress = if (goal > 0) (total.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val goalMet = goal in 1..total
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            // Verde al cumplir la meta
            indicatorColor = if (goalMet) AppColors.Green else AppColors.LightWater,
            trackColor = AppColors.DarkGray,
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${total.withThousands()} ml", style = MaterialTheme.typography.title2)
            Text("de ${goal.withThousands()} ml", style = MaterialTheme.typography.caption2, color = Color.LightGray)
            Text(
                if (goalMet) "¡Meta cumplida!" else "${(progress * 100).roundToInt()} %",
                style = MaterialTheme.typography.caption3,
                color = if (goalMet) AppColors.Green else AppColors.LightWater,
            )
        }
    }
}
