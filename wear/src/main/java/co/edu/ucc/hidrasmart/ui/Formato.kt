package co.edu.ucc.hidrasmart.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es: Locale = Locale.forLanguageTag("es-CO")
private val zona: ZoneId = ZoneId.systemDefault()
private val formatoHora = DateTimeFormatter.ofPattern("h:mm a", es)
private val formatoDia = DateTimeFormatter.ofPattern("EEE d MMM", es)

fun Long.aFecha(): LocalDate = Instant.ofEpochMilli(this).atZone(zona).toLocalDate()

fun Long.hora(): String = Instant.ofEpochMilli(this).atZone(zona).format(formatoHora)

fun LocalDate.nombre(): String = when (this) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> format(formatoDia)
}

/** 1250 → "1.250" */
fun Int.miles(): String = NumberFormat.getIntegerInstance(es).format(this)

/** 1.25 → "1,3" con [decimales] decimales */
fun Double.dec(decimales: Int = 1): String = String.format(es, "%.${decimales}f", this)

/** 3725000 ms → "1:02:05"; 125000 → "02:05" */
fun duracion(ms: Long): String {
    val totalSeg = (ms / 1000).coerceAtLeast(0)
    val h = totalSeg / 3600
    val m = (totalSeg % 3600) / 60
    val s = totalSeg % 60
    return if (h > 0) String.format(es, "%d:%02d:%02d", h, m, s) else String.format(es, "%02d:%02d", m, s)
}
