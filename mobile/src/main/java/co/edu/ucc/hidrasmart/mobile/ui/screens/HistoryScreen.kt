package co.edu.ucc.hidrasmart.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.ucc.hidrasmart.format.displayName
import co.edu.ucc.hidrasmart.format.formatDuration
import co.edu.ucc.hidrasmart.format.toLocalDate
import co.edu.ucc.hidrasmart.format.toTimeText
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.mobile.MobileViewModel
import co.edu.ucc.hidrasmart.mobile.ui.components.NumberField
import co.edu.ucc.hidrasmart.mobile.ui.theme.BrandColors

/** Últimos 30 días con agua tomada y entrenamientos (del reloj o registrados aquí). */
@Composable
fun HistoryScreen(vm: MobileViewModel) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val goal = vm.profile.dailyGoalMl
    val days = (vm.intakes.map { it.timestamp.toLocalDate() } + vm.sessions.map { it.startTime.toLocalDate() })
        .distinct()
        .sortedDescending()
        .take(30)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (days.isEmpty()) {
                item { Text("Aún no hay registros. Empieza registrando una toma en Inicio.") }
            }
            items(days) { day ->
                val total = vm.intakes.filter { it.timestamp.toLocalDate() == day }.sumOf { it.ml }
                val workouts = vm.sessions.filter { it.startTime.toLocalDate() == day }.sortedByDescending { it.startTime }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day.displayName(), style = MaterialTheme.typography.titleMedium)
                            Text("${total.withThousands()} / ${goal.withThousands()} ml", style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(
                            progress = { if (goal > 0) (total.toFloat() / goal).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (total >= goal) BrandColors.Green else BrandColors.Water,
                        )
                        workouts.forEach { s ->
                            Text(
                                "${s.type} · ${s.startTime.toTimeText()} · ${formatDuration(s.durationSec * 1000)} · " +
                                    "tomó ${s.drankMl} ml" + if (s.recommendedMl > 0) " de ${s.recommendedMl} ml" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            text = { Text("Entrenamiento") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAdd) {
        AddWorkoutDialog(
            onDismiss = { showAdd = false },
            onSave = { type, minutes, drank, hr ->
                vm.addWorkout(type, minutes, drank, hr)
                showAdd = false
            },
        )
    }
}

/** Registrar un entrenamiento hecho sin el reloj: también entrena el modelo de IA. */
@Composable
private fun AddWorkoutDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, Int?) -> Unit) {
    var type by rememberSaveable { mutableStateOf(MobileViewModel.WORKOUT_TYPES.first()) }
    var minutes by rememberSaveable { mutableStateOf("60") }
    var drank by rememberSaveable { mutableStateOf("500") }
    var heartRate by rememberSaveable { mutableStateOf("") }
    val m = minutes.toIntOrNull()
    val d = drank.toIntOrNull()
    val valid = m != null && m in 5..600 && d != null && d in 0..10000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar entrenamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MobileViewModel.WORKOUT_TYPES) { t ->
                        FilterChip(selected = t == type, onClick = { type = t }, label = { Text(t) })
                    }
                }
                NumberField("Duración", minutes, { minutes = it.take(3) }, suffix = "min")
                NumberField("Agua tomada", drank, { drank = it.take(5) }, suffix = "ml")
                NumberField("FC media (opcional)", heartRate, { heartRate = it.take(3) }, suffix = "lpm")
                Text(
                    "Cada entrenamiento registrado ayuda al modelo de IA a predecir cuánta agua necesitas.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (m != null && d != null) onSave(type, m, d, heartRate.toIntOrNull()?.takeIf { it in 40..230 }) },
                enabled = valid,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
