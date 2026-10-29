package co.edu.ucc.hidrasmart.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.Note
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.ScreenTitle
import co.edu.ucc.hidrasmart.format.toLocalDate
import co.edu.ucc.hidrasmart.format.formatDuration
import co.edu.ucc.hidrasmart.format.toTimeText
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.format.displayName
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import java.time.LocalDate

@Composable
fun HistoryScreen(vm: AppViewModel, onOpenDay: (String) -> Unit) {
    val days = (vm.intakes.map { it.timestamp.toLocalDate() } + vm.sessions.map { it.startTime.toLocalDate() })
        .distinct()
        .sortedDescending()
        .take(30)

    WearScreen {
        item { ScreenTitle("Historial") }
        if (days.isEmpty()) {
            item { Note("Aún no hay registros. Empieza registrando una toma.") }
        }
        days.forEach { day ->
            val total = vm.intakes.filter { it.timestamp.toLocalDate() == day }.sumOf { it.ml }
            val workouts = vm.sessions.count { it.startTime.toLocalDate() == day }
            item {
                ActionChip(
                    day.displayName(), AppColors.DarkGray, { onOpenDay(day.toString()) },
                    detail = "${total.withThousands()} ml · $workouts entreno${if (workouts == 1) "" else "s"}",
                )
            }
        }
    }
}

@Composable
fun DayDetailScreen(vm: AppViewModel, date: String, onOpenSession: (Long) -> Unit) {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: LocalDate.now()
    val intakes = vm.intakes.filter { it.timestamp.toLocalDate() == day }.sortedByDescending { it.timestamp }
    val sessions = vm.sessions.filter { it.startTime.toLocalDate() == day }.sortedByDescending { it.startTime }
    val total = intakes.sumOf { it.ml }

    WearScreen {
        item { ScreenTitle(day.displayName(), "${total.withThousands()} de ${vm.profile.dailyGoalMl.withThousands()} ml") }
        sessions.forEach { s ->
            item {
                ActionChip(
                    s.type, AppColors.Blue, { onOpenSession(s.id) },
                    detail = "${s.startTime.toTimeText()} · ${formatDuration(s.durationSec * 1000)}",
                    icon = Icons.Default.PlayArrow,
                )
            }
        }
        if (intakes.isNotEmpty()) {
            item { Note("Tomas") }
        }
        intakes.forEach { t ->
            item { Text("${t.timestamp.toTimeText()} · ${t.ml} ml", style = MaterialTheme.typography.body2) }
        }
    }
}

@Composable
fun SessionDetailScreen(vm: AppViewModel, id: Long) {
    val s = vm.sessions.firstOrNull { it.id == id }
    WearScreen {
        if (s == null) {
            item { Note("No se encontró la sesión") }
            return@WearScreen
        }
        item { ScreenTitle(s.type, "${s.startTime.toLocalDate().displayName()} · ${s.startTime.toTimeText()}") }
        item { Text(formatDuration(s.durationSec * 1000), style = MaterialTheme.typography.display3) }
        item { Note("FC media: ${s.avgHeartRate?.let { "$it lpm" } ?: "sin datos"}", Color.White) }
        item { Note("Tomado: ${s.drankMl.withThousands()} ml", Color.White) }
        item { Note("Recomendado: ${s.recommendedMl.withThousands()} ml", Color.White) }
    }
}
