package co.edu.ucc.hidrasmart.ui.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.ui.components.ActionChip
import co.edu.ucc.hidrasmart.ui.components.NumberStepper
import co.edu.ucc.hidrasmart.ui.components.ScreenTitle
import co.edu.ucc.hidrasmart.ui.components.WearScreen
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cantidades de toma rápida (inicio y pantalla "Registrar toma"). */
val QUICK_AMOUNTS = listOf(150, 250, 500)

@Composable
fun LogIntakeScreen(vm: AppViewModel, onLogged: (Int) -> Unit, onCustomAmount: () -> Unit) {
    WearScreen {
        item { ScreenTitle("Registrar toma", "Hoy: ${vm.todayTotalMl.withThousands()} ml") }
        QUICK_AMOUNTS.forEach { ml ->
            item {
                ActionChip("+$ml ml", AppColors.Water, {
                    vm.logIntake(ml)
                    onLogged(ml)
                })
            }
        }
        item { ActionChip("Otra cantidad", AppColors.Gray, onCustomAmount) }
    }
}

@Composable
fun CustomAmountScreen(vm: AppViewModel, onLogged: (Int) -> Unit) {
    var ml by rememberSaveable { mutableIntStateOf(300) }
    WearScreen {
        item { ScreenTitle("Otra cantidad") }
        item { NumberStepper("Mililitros", "$ml", { ml = (ml - 50).coerceAtLeast(50) }, { ml = (ml + 50).coerceAtMost(2000) }) }
        item {
            ActionChip("Registrar", AppColors.Green, {
                vm.logIntake(ml)
                onLogged(ml)
            })
        }
    }
}

/**
 * Confirmación animada después de registrar una toma: vibra, aparece un círculo verde con ✓
 * y el anillo del borde avanza desde lo que llevabas hasta el total nuevo del día.
 * Vuelve sola a la pantalla anterior, o antes si se toca la pantalla.
 */
@Composable
fun ConfirmationScreen(ml: Int, totalMl: Int, goalMl: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val checkScale = remember { Animatable(0f) }
    val before = if (goalMl > 0) ((totalMl - ml).toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val after = if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val ring = remember { Animatable(before) }
    val goalMet = goalMl in 1..totalMl
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        vibrateConfirm(context)
        launch {
            checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        launch { ring.animateTo(after, tween(durationMillis = 900, delayMillis = 150)) }
        delay(1800)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable { finish() },
        contentAlignment = Alignment.Center,
    ) {
        if (goalMl > 0) {
            CircularProgressIndicator(
                progress = ring.value,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                indicatorColor = if (goalMet) AppColors.Green else AppColors.LightWater,
                trackColor = AppColors.DarkGray,
                strokeWidth = 6.dp,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .scale(checkScale.value)
                    .background(AppColors.Green, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("+$ml ml", style = MaterialTheme.typography.title2)
            Text(
                if (goalMet) "¡Meta cumplida!" else "Hoy: ${totalMl.withThousands()} ml",
                style = MaterialTheme.typography.caption2,
                color = if (goalMet) AppColors.Green else Color.LightGray,
            )
        }
    }
}

/** Vibración corta de "hecho" (doble toque). */
private fun vibrateConfirm(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }
}
