package co.edu.ucc.hidrasmart.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Un color por tipo de acción (ver la tabla de botones de la documentación). */
object AppColors {
    val Blue = Color(0xFF1E88E5)       // Iniciar entrenamiento
    val Water = Color(0xFF0288D1)       // Registrar toma
    val LightWater = Color(0xFF4FC3F7)  // Anillo de progreso
    val Green = Color(0xFF2E7D32)      // Ya tomé / confirmar
    val Gray = Color(0xFF616161)       // En 5 min / secundario
    val Amber = Color(0xFFF9A825)      // Pausar
    val Red = Color(0xFFC62828)       // Terminar
    val DarkGray = Color(0xFF2B2B2B) // Ajustes, historial
}

@Composable
fun HidraSmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = AppColors.Blue,
            secondary = AppColors.LightWater,
            error = AppColors.Red,
        ),
        content = content,
    )
}
