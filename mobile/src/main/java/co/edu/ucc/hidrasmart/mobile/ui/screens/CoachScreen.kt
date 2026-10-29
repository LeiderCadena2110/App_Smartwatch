package co.edu.ucc.hidrasmart.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.edu.ucc.hidrasmart.ai.LocalCoach
import co.edu.ucc.hidrasmart.ai.PlanSource
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.mobile.ChatMessage
import co.edu.ucc.hidrasmart.mobile.MobileViewModel
import co.edu.ucc.hidrasmart.mobile.ui.components.AiCard
import co.edu.ucc.hidrasmart.mobile.ui.theme.BrandColors
import kotlin.math.roundToInt

/** Coach de IA: planificador con el modelo del dispositivo y chat con Gemini (o IA local). */
@Composable
fun CoachScreen(vm: MobileViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Bajar al último mensaje cuando llega uno nuevo
    LaunchedEffect(vm.messages.size, vm.thinking) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PlannerCard(vm) }
            item {
                Text(
                    if (vm.cloudAiEnabled) "Chat con Gemini. Si no hay internet, responde la IA local."
                    else "IA local (sin internet). Agrega tu clave de Gemini en Perfil para respuestas generativas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(vm.messages) { message -> ChatBubble(message) }
            if (vm.thinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandColors.Ai)
                        Text("Pensando…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LocalCoach.SUGGESTED_QUESTIONS) { q ->
                SuggestionChip(onClick = { vm.send(q) }, label = { Text(q) }, enabled = !vm.thinking)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(300) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pregúntale al coach…") },
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !vm.thinking,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
                else BrandColors.Ai.copy(alpha = 0.12f),
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                if (!message.fromUser) {
                    Text(
                        message.note ?: if (message.fromCloud) "Gemini" else "IA local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** El modelo entrenado en el teléfono predice cuánta agua llevar a un entrenamiento. */
@Composable
private fun PlannerCard(vm: MobileViewModel) {
    var type by rememberSaveable { mutableStateOf(MobileViewModel.WORKOUT_TYPES.first()) }
    var minutes by rememberSaveable { mutableFloatStateOf(60f) }
    val plan = remember(type, minutes, vm.sessions, vm.profile) { vm.waterPlan(type, minutes.roundToInt()) }

    AiCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Planear entrenamiento", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MobileViewModel.WORKOUT_TYPES) { t ->
                    FilterChip(selected = t == type, onClick = { type = t }, label = { Text(t) })
                }
            }
            Text("${minutes.roundToInt()} minutos", style = MaterialTheme.typography.bodyMedium)
            Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 15f..240f, steps = 14)
            Text(
                "Lleva ${plan.totalMl.withThousands()} ml",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                when (plan.source) {
                    PlanSource.PERSONAL_MODEL -> "Modelo IA entrenado con ${plan.samples} entrenamientos " +
                        "(ajuste ${((plan.rSquared ?: 0.0) * 100).roundToInt()} %) · ${plan.perHourMl} ml/h"
                    PlanSource.SWEAT_TEST -> "Según tu prueba de sudoración (${plan.perHourMl} ml/h). " +
                        "Registra 3 entrenamientos para activar el modelo IA."
                    PlanSource.GENERAL_GUIDE -> "Guía general 400–800 ml/h. Haz la prueba de sudoración en Perfil."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
