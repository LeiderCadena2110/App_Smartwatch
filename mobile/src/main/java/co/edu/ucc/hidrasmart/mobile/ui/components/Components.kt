package co.edu.ucc.hidrasmart.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.ucc.hidrasmart.ai.RiskLevel
import co.edu.ucc.hidrasmart.format.withThousands
import co.edu.ucc.hidrasmart.mobile.ui.theme.BrandColors
import kotlin.math.roundToInt

fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.HIGH -> BrandColors.Red
    RiskLevel.MEDIUM -> BrandColors.Amber
    RiskLevel.LOW -> BrandColors.Green
}

/** Anillo grande con lo tomado hoy frente a la meta; se pone verde al cumplirla. */
@Composable
fun WaterRing(totalMl: Int, goalMl: Int, size: Dp = 200.dp) {
    val progress = if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val met = goalMl in 1..totalMl
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = if (met) BrandColors.Green else BrandColors.Water,
            strokeWidth = 14.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${totalMl.withThousands()} ml", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("de ${goalMl.withThousands()} ml", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (met) "¡Meta cumplida!" else "${(progress * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelLarge,
                color = if (met) BrandColors.Green else BrandColors.Water,
            )
        }
    }
}

/** Tarjeta de un hallazgo de la IA, con un punto de color según el riesgo. */
@Composable
fun InsightCard(title: String, message: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).background(accent, CircleShape))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Tarjeta resaltada con el color de la IA. */
@Composable
fun AiCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Ai.copy(alpha = 0.12f)),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Campo numérico: solo acepta dígitos (y coma o punto si [decimal]). */
@Composable
fun NumberField(
    label: String,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = text,
        onValueChange = { input -> onText(input.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** "70,5" o "70.5" → 70.5 */
fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()
