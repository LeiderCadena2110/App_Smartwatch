package co.edu.ucc.hidrasmart.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.ai.LocalCoach
import co.edu.ucc.hidrasmart.ai.PlanSource
import co.edu.ucc.hidrasmart.ai.RiskLevel
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.ui.components.ActionChip
import co.edu.ucc.hidrasmart.ui.components.InsightCard
import co.edu.ucc.hidrasmart.ui.components.Note
import co.edu.ucc.hidrasmart.ui.components.NumberStepper
import co.edu.ucc.hidrasmart.ui.components.ScreenTitle
import co.edu.ucc.hidrasmart.ui.components.WearScreen
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlin.math.roundToInt

/** Color del punto según el nivel de riesgo del hallazgo. */
fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.HIGH -> AppColors.Red
    RiskLevel.MEDIUM -> AppColors.Amber
    RiskLevel.LOW -> AppColors.Green
}

/**
 * Coach de IA en el reloj: hallazgos del sistema experto, respuesta de Gemini (o de la IA local
 * si no hay internet), preguntas rápidas y por voz, y acceso al planificador.
 */
@Composable
fun CoachScreen(vm: AppViewModel, onOpenPlan: () -> Unit) {
    val insights = remember(vm.intakes, vm.sessions, vm.profile) { vm.insights() }
    var question by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    fun ask(text: String?) {
        question = text
        voiceError = null
        vm.askCoach(text)
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) ask(spoken)
    }

    // Al abrir, un consejo para este momento
    LaunchedEffect(Unit) {
        if (vm.coachAnswer == null && !vm.coachLoading) ask(null)
    }

    WearScreen {
        item { ScreenTitle("Coach IA", if (vm.cloudAiEnabled) "Gemini + IA en el reloj" else "IA en el reloj · sin internet") }

        val answer = vm.coachAnswer
        when {
            vm.coachLoading -> item {
                CircularProgressIndicator(indicatorColor = AppColors.Ai, modifier = Modifier.size(28.dp))
            }
            answer != null -> {
                item { InsightCard(question ?: "Consejo para ahora", answer.text, AppColors.Ai) }
                item {
                    Note(
                        answer.note ?: if (answer.fromCloud) "Respuesta de Gemini" else "Respuesta de la IA local",
                        AppColors.AiLight,
                    )
                }
            }
        }

        item {
            ActionChip(
                "Preguntar con voz", AppColors.Ai,
                {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres saber?")
                    try {
                        voiceLauncher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        voiceError = "Este reloj no tiene dictado por voz: usa las preguntas rápidas."
                    }
                },
                icon = Icons.Default.Star,
            )
        }
        voiceError?.let { item { Note(it, AppColors.Amber) } }

        LocalCoach.SUGGESTED_QUESTIONS.forEach { q ->
            item { ActionChip(q, AppColors.DarkGray, { ask(q) }) }
        }

        item {
            ActionChip(
                "Planear entrenamiento", AppColors.Blue, onOpenPlan,
                detail = "Modelo IA: cuánta agua llevar",
                icon = Icons.Default.PlayArrow,
            )
        }

        item { ScreenTitle("Tu análisis") }
        insights.take(4).forEach { insight ->
            item { InsightCard(insight.title, insight.message, riskColor(insight.level)) }
        }
        item { Note("Orientación general, no reemplaza a un profesional de la salud.") }
    }
}

/** Planificador: el modelo entrenado en el reloj predice cuánta agua llevar. */
@Composable
fun PlanScreen(vm: AppViewModel) {
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    val type = WORKOUT_TYPES[typeIndex % WORKOUT_TYPES.size]
    val plan = remember(type, minutes, vm.sessions, vm.profile) { vm.waterPlan(type, minutes) }

    WearScreen {
        item { ScreenTitle("Planear", "Cuánta agua llevar") }
        item {
            ActionChip(type, AppColors.Blue, { typeIndex = (typeIndex + 1) % WORKOUT_TYPES.size }, detail = "Toca para cambiar")
        }
        item {
            NumberStepper(
                "Minutos", "$minutes",
                { minutes = (minutes - 15).coerceAtLeast(15) },
                { minutes = (minutes + 15).coerceAtMost(300) },
            )
        }
        item { Text("${plan.totalMl.withThousands()} ml", style = MaterialTheme.typography.display3, color = AppColors.LightWater) }
        item { Note("${plan.perHourMl} ml por hora · ${plan.source.label}", Color.White) }
        item {
            Note(
                when (plan.source) {
                    PlanSource.PERSONAL_MODEL ->
                        "Aprendido de ${plan.samples} entrenos · ajuste ${((plan.rSquared ?: 0.0) * 100).roundToInt()} %"
                    PlanSource.SWEAT_TEST -> "Con tu prueba de sudoración. Con 3 entrenos más se activa el modelo IA."
                    PlanSource.GENERAL_GUIDE -> "Guía general (400–800 ml/h). Haz la prueba de sudoración para personalizar."
                },
            )
        }
    }
}
