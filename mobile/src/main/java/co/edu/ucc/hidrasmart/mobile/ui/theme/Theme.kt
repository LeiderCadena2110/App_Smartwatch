package co.edu.ucc.hidrasmart.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mismos colores por acción que en el reloj, para que las dos apps se sientan iguales. */
object BrandColors {
    val Water = Color(0xFF0288D1)
    val LightWater = Color(0xFF4FC3F7)
    val Blue = Color(0xFF1E88E5)
    val Green = Color(0xFF2E7D32)
    val Amber = Color(0xFFF9A825)
    val Red = Color(0xFFC62828)
    val Ai = Color(0xFF7E57C2)
}

private val LightColors = lightColorScheme(
    primary = BrandColors.Water,
    secondary = BrandColors.LightWater,
    tertiary = BrandColors.Ai,
    error = BrandColors.Red,
)

private val DarkColors = darkColorScheme(
    primary = BrandColors.LightWater,
    secondary = BrandColors.Water,
    tertiary = Color(0xFFB39DDB),
    error = Color(0xFFEF9A9A),
)

@Composable
fun HidraSmartMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
