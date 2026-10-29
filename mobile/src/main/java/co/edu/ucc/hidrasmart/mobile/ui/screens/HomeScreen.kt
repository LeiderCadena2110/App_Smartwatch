package co.edu.ucc.hidrasmart.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.mobile.MobileViewModel
import co.edu.ucc.hidrasmart.mobile.ui.components.AiCard
import co.edu.ucc.hidrasmart.mobile.ui.components.InsightCard
import co.edu.ucc.hidrasmart.mobile.ui.components.NumberField
import co.edu.ucc.hidrasmart.mobile.ui.components.SectionTitle
import co.edu.ucc.hidrasmart.mobile.ui.components.WaterRing
import co.edu.ucc.hidrasmart.mobile.ui.components.riskColor

private val QUICK_AMOUNTS = listOf(150, 250, 500)

@Composable
fun HomeScreen(vm: MobileViewModel, onOpenCoach: () -> Unit) {
    val insights = remember(vm.intakes, vm.sessions, vm.profile) { vm.insights() }
    val plan = remember(vm.sessions, vm.profile) { vm.waterPlan(null, 60) }
    var showCustom by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { WaterRing(vm.todayTotalMl, vm.profile.dailyGoalMl) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_AMOUNTS.forEach { ml ->
                    FilledTonalButton(onClick = { vm.logIntake(ml) }) { Text("+$ml ml") }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showCustom = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Otra cantidad", Modifier.padding(start = 8.dp))
            }
        }

        item {
            AiCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Plan para 1 hora de ejercicio", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${plan.totalMl.withThousands()} ml",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text("${plan.perHourMl} ml/h · ${plan.source.label}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenCoach) { Text("Preguntarle al coach IA") }
                }
            }
        }

        item { SectionTitle("Tu análisis", Modifier.fillMaxWidth()) }
        items(insights.take(4)) { insight ->
            InsightCard(insight.title, insight.message, riskColor(insight.level))
        }
    }

    if (showCustom) {
        CustomAmountDialog(
            onDismiss = { showCustom = false },
            onConfirm = { ml ->
                vm.logIntake(ml)
                showCustom = false
            },
        )
    }
}

@Composable
private fun CustomAmountDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("300") }
    val ml = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar toma") },
        text = { NumberField("Cantidad", text, { text = it.take(4) }, suffix = "ml") },
        confirmButton = {
            Button(onClick = { ml?.let(onConfirm) }, enabled = ml != null && ml in 10..3000) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
