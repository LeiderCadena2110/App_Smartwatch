package co.edu.ucc.hidrasmart.ui.pantallas

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
import co.edu.ucc.hidrasmart.ui.ChipAccion
import co.edu.ucc.hidrasmart.ui.Pantalla
import co.edu.ucc.hidrasmart.ui.duracion
import co.edu.ucc.hidrasmart.ui.miles
import co.edu.ucc.hidrasmart.ui.tema.Colores

@Composable
fun PantallaInicio(vm: AppViewModel, ir: (String) -> Unit) {
    val total = vm.totalHoyMl
    val meta = vm.perfil.metaDiariaMl
    val sesion = vm.sesion

    Pantalla {
        item { AnilloProgreso(total, meta) }

        if (!vm.perfilConfigurado) {
            item { ChipAccion("Configura tu perfil", Colores.Ambar, { ir(Rutas.PERFIL) }, icono = Icons.Default.Person) }
        }
        if (sesion != null) {
            item {
                ChipAccion(
                    "Volver a la sesión", Colores.Azul, { ir(Rutas.SESION) },
                    detalle = "${sesion.tipo} · ${duracion(vm.duracionMs(sesion))}",
                    icono = Icons.Default.PlayArrow,
                )
            }
        }
        item { ChipAccion("Registrar toma", Colores.Agua, { ir(Rutas.REGISTRAR) }, icono = Icons.Default.Add) }
        if (sesion == null) {
            item { ChipAccion("Entrenar", Colores.Azul, { ir(Rutas.TIPO) }, icono = Icons.Default.PlayArrow) }
        }
        item { ChipAccion("Historial", Colores.GrisOscuro, { ir(Rutas.HISTORIAL) }, icono = Icons.Default.DateRange) }
        item { ChipAccion("Perfil y ajustes", Colores.GrisOscuro, { ir(Rutas.PERFIL) }, icono = Icons.Default.Settings) }
    }
}

/** Anillo con lo tomado hoy frente a la meta diaria. */
@Composable
private fun AnilloProgreso(total: Int, meta: Int) {
    val progreso = if (meta > 0) (total.toFloat() / meta).coerceIn(0f, 1f) else 0f
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progreso,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = Colores.AguaClara,
            trackColor = Colores.GrisOscuro,
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${total.miles()} ml", style = MaterialTheme.typography.title2)
            Text("de ${meta.miles()} ml", style = MaterialTheme.typography.caption2, color = Color.LightGray)
        }
    }
}
