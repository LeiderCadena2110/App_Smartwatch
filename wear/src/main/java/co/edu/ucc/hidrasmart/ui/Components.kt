package co.edu.ucc.hidrasmart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TitleCard
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import co.edu.ucc.hidrasmart.ui.theme.AppColors

/** Estructura común de cada pantalla: hora arriba, lista que se escala en los bordes y barra de posición. */
@Composable
fun WearScreen(
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    edge: (@Composable () -> Unit)? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        Box(Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                content = content,
            )
            // Elemento dibujado sobre el borde de la pantalla redonda (por ejemplo, una cuenta regresiva)
            edge?.invoke()
        }
    }
}

/**
 * Arco sobre el borde de la pantalla que se vacía con el tiempo. Deja libre la parte
 * de arriba, donde va la hora.
 */
@Composable
fun EdgeProgress(progress: Float, color: Color) {
    CircularProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = Modifier.fillMaxSize().padding(3.dp),
        startAngle = 300f,
        endAngle = 240f,
        indicatorColor = color,
        trackColor = color.copy(alpha = 0.15f),
        strokeWidth = 5.dp,
    )
}

/** Tarjeta con un hallazgo del coach de IA: punto de color según el riesgo, título y mensaje. */
@Composable
fun InsightCard(title: String, message: String, accent: Color, onClick: () -> Unit = {}) {
    TitleCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        },
    ) {
        Text(message, style = MaterialTheme.typography.caption2, color = Color.LightGray)
    }
}

/** Chip de ancho completo (52 dp de alto) con el color de su acción. */
@Composable
fun ActionChip(
    text: String,
    color: Color,
    onClick: () -> Unit,
    detail: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (detail != null) {
            { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(ChipDefaults.IconSize)) }
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
fun RoundButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    buttonSize: Dp = ButtonDefaults.SmallButtonSize,
    description: String = text,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(buttonSize).semantics { contentDescription = description },
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = color, contentColor = Color.White),
    ) {
        Text(text, fontSize = 20.sp)
    }
}

/** Fila para cambiar un número con botones − y + (en el reloj no hay teclado cómodo). */
@Composable
fun NumberStepper(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.caption2, color = Color.LightGray)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton("−", AppColors.Gray, onDecrease, description = "Menos")
            Text(value, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            RoundButton("+", AppColors.Gray, onIncrease, description = "Más")
        }
    }
}

/** Título centrado para la parte de arriba de una lista. */
@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.caption2, color = Color.LightGray, textAlign = TextAlign.Center)
        }
    }
}

/** Texto informativo pequeño y centrado. */
@Composable
fun Note(text: String, color: Color = Color.LightGray) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.caption2,
        color = color,
        textAlign = TextAlign.Center,
    )
}
