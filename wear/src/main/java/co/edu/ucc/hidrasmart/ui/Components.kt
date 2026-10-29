package co.edu.ucc.hidrasmart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import co.edu.ucc.hidrasmart.ui.theme.Colores

/** Estructura común de cada pantalla: hora arriba, lista que se escala en los bordes y barra de posición. */
@Composable
fun Pantalla(
    estado: ScalingLazyListState = rememberScalingLazyListState(),
    contenido: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = estado) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = estado,
            content = contenido,
        )
    }
}

/** Chip de ancho completo (52 dp de alto) con el color de su acción. */
@Composable
fun ChipAccion(
    texto: String,
    color: Color,
    onClick: () -> Unit,
    detalle: String? = null,
    icono: ImageVector? = null,
    habilitado: Boolean = true,
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = habilitado,
        label = { Text(texto, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (detalle != null) {
            { Text(detalle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        icon = if (icono != null) {
            { Icon(icono, contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = ChipDefaults.primaryChipColors(
            backgroundColor = color,
            contentColor = Color.White,
            secondaryContentColor = Color.White.copy(alpha = 0.85f),
            iconColor = Color.White,
        ),
    )
}

/** Botón redondo con texto (por ejemplo "+" o "−"). Mínimo 48 dp, el mínimo táctil de Wear OS. */
@Composable
fun BotonRedondo(
    texto: String,
    color: Color,
    onClick: () -> Unit,
    tamano: Dp = ButtonDefaults.SmallButtonSize,
    descripcion: String = texto,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(tamano).semantics { contentDescription = descripcion },
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = color, contentColor = Color.White),
    ) {
        Text(texto, fontSize = 20.sp)
    }
}

/** Fila para cambiar un número con botones − y + (en el reloj no hay teclado cómodo). */
@Composable
fun FilaNumero(etiqueta: String, valor: String, onMenos: () -> Unit, onMas: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(etiqueta, style = MaterialTheme.typography.caption2, color = Color.LightGray)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonRedondo("−", Colores.Gris, onMenos, descripcion = "Menos")
            Text(valor, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            BotonRedondo("+", Colores.Gris, onMas, descripcion = "Más")
        }
    }
}

/** Título centrado para la parte de arriba de una lista. */
@Composable
fun Titulo(texto: String, subtitulo: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(texto, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        if (subtitulo != null) {
            Text(subtitulo, style = MaterialTheme.typography.caption2, color = Color.LightGray, textAlign = TextAlign.Center)
        }
    }
}

/** Texto informativo pequeño y centrado. */
@Composable
fun Nota(texto: String, color: Color = Color.LightGray) {
    Text(
        texto,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.caption2,
        color = color,
        textAlign = TextAlign.Center,
    )
}
