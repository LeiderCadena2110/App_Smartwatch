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
import co.edu.ucc.hidrasmart.ui.toLocalDate
import co.edu.ucc.hidrasmart.ui.formatDuration
import co.edu.ucc.hidrasmart.ui.toTimeText
import co.edu.ucc.hidrasmart.ui.withThousands
import co.edu.ucc.hidrasmart.ui.displayName
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import java.time.LocalDate

@Composable
fun PantallaHistorial(vm: AppViewModel, abrirDia: (String) -> Unit) {
    val dias = (vm.intakes.map { it.timestamp.toLocalDate() } + vm.sessions.map { it.startTime.toLocalDate() })
        .distinct()
        .sortedDescending()
        .take(30)

    WearScreen {
        item { ScreenTitle("Historial") }
        if (dias.isEmpty()) {
            item { Note("Aún no hay registros. Empieza registrando una toma.") }
        }
        dias.forEach { dia ->
            val total = vm.intakes.filter { it.timestamp.toLocalDate() == dia }.sumOf { it.ml }
            val entrenos = vm.sessions.count { it.startTime.toLocalDate() == dia }
            item {
                ActionChip(
                    dia.displayName(), AppColors.DarkGray, { abrirDia(dia.toString()) },
                    detail = "${total.withThousands()} ml · $entrenos entreno${if (entrenos == 1) "" else "s"}",
                )
            }
        }
    }
}

@Composable
fun PantallaDetalleDia(vm: AppViewModel, fecha: String, abrirSesion: (Long) -> Unit) {
    val dia = runCatching { LocalDate.parse(fecha) }.getOrNull() ?: LocalDate.now()
    val intakes = vm.intakes.filter { it.timestamp.toLocalDate() == dia }.sortedByDescending { it.timestamp }
    val sessions = vm.sessions.filter { it.startTime.toLocalDate() == dia }.sortedByDescending { it.startTime }
    val total = intakes.sumOf { it.ml }

    WearScreen {
        item { ScreenTitle(dia.displayName(), "${total.withThousands()} de ${vm.profile.dailyGoalMl.withThousands()} ml") }
        sessions.forEach { s ->
            item {
                ActionChip(
                    s.type, AppColors.Blue, { abrirSesion(s.id) },
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
fun PantallaDetalleSesion(vm: AppViewModel, id: Long) {
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
