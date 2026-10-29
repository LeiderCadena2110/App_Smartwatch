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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.formatDuration
import co.edu.ucc.hidrasmart.ui.withThousands
import co.edu.ucc.hidrasmart.ui.theme.AppColors

@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val total = vm.totalHoyMl
    val goal = vm.profile.dailyGoalMl
    val session = vm.session

    WearScreen {
        item { ProgressRing(total, goal) }

        if (!vm.isProfileConfigured) {
            item { ActionChip("Configura tu perfil", AppColors.Amber, { onNavigate(Routes.PROFILE) }, icon = Icons.Default.Person) }
        }
        if (session != null) {
            item {
                ActionChip(
                    "Volver a la sesión", AppColors.Blue, { onNavigate(Routes.SESSION) },
                    detail = "${session.type} · ${formatDuration(vm.duracionMs(session))}",
                    icon = Icons.Default.PlayArrow,
                )
            }
        }
        item { ActionChip("Registrar toma", AppColors.Water, { onNavigate(Routes.LOG_INTAKE) }, icon = Icons.Default.Add) }
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
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = AppColors.LightWater,
            trackColor = AppColors.DarkGray,
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${total.withThousands()} ml", style = MaterialTheme.typography.title2)
            Text("de ${goal.withThousands()} ml", style = MaterialTheme.typography.caption2, color = Color.LightGray)
        }
    }
}
