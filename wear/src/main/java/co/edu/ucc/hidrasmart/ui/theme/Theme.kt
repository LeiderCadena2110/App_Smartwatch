package co.edu.ucc.hidrasmart.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Un color por tipo de acción (ver la tabla de botones de la documentación). */
object Colores {
    val Azul = Color(0xFF1E88E5)       // Iniciar entrenamiento
    val Agua = Color(0xFF0288D1)       // Registrar toma
    val AguaClara = Color(0xFF4FC3F7)  // Anillo de progreso
    val Verde = Color(0xFF2E7D32)      // Ya tomé / confirmar
    val Gris = Color(0xFF616161)       // En 5 min / secundario
    val Ambar = Color(0xFFF9A825)      // Pausar
    val Rojo = Color(0xFFC62828)       // Terminar
    val GrisOscuro = Color(0xFF2B2B2B) // Ajustes, historial
}

@Composable
fun HidraTema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Colores.Azul,
            secondary = Colores.AguaClara,
            error = Colores.Rojo,
        ),
        content = contenido,
    )
}
