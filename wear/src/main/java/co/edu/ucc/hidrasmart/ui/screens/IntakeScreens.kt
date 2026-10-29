package co.edu.ucc.hidrasmart.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import co.edu.ucc.hidrasmart.AppViewModel
import co.edu.ucc.hidrasmart.ui.ActionChip
import co.edu.ucc.hidrasmart.ui.NumberStepper
import co.edu.ucc.hidrasmart.ui.WearScreen
import co.edu.ucc.hidrasmart.ui.ScreenTitle
import co.edu.ucc.hidrasmart.ui.withThousands
import co.edu.ucc.hidrasmart.ui.theme.AppColors
import kotlinx.coroutines.delay

private val CANTIDADES_RAPIDAS = listOf(150, 250, 500)

@Composable
fun PantallaRegistrar(vm: AppViewModel, registrado: (Int) -> Unit, otraCantidad: () -> Unit) {
    WearScreen {
        item { ScreenTitle("Registrar toma", "Hoy: ${vm.totalHoyMl.withThousands()} ml") }
        CANTIDADES_RAPIDAS.forEach { ml ->
            item {
                ActionChip("+$ml ml", AppColors.Water, {
                    vm.logIntake(ml)
                    registrado(ml)
                })
            }
        }
        item { ActionChip("Otra cantidad", AppColors.Gray, otraCantidad) }
    }
}

@Composable
fun PantallaOtraCantidad(vm: AppViewModel, registrado: (Int) -> Unit) {
    var ml by rememberSaveable { mutableIntStateOf(300) }
    WearScreen {
        item { ScreenTitle("Otra cantidad") }
        item { NumberStepper("Mililitros", "$ml", { ml = (ml - 50).coerceAtLeast(50) }, { ml = (ml + 50).coerceAtMost(2000) }) }
        item {
            ActionChip("Registrar", AppColors.Green, {
                vm.logIntake(ml)
                registrado(ml)
            })
        }
    }
}

/** Confirmación breve que vuelve sola a la pantalla anterior. */
@Composable
fun PantallaConfirmacion(ml: Int, listo: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        listo()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Green, modifier = Modifier.size(48.dp))
            Text("+$ml ml", style = MaterialTheme.typography.title1)
            Text("Registrado", style = MaterialTheme.typography.caption1)
        }
    }
}
