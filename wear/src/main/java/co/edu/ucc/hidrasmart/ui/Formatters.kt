package co.edu.ucc.hidrasmart.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es: Locale = Locale.forLanguageTag("es-CO")
private val zone: ZoneId = ZoneId.systemDefault()
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", es)
private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", es)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun Long.toTimeText(): String = Instant.ofEpochMilli(this).atZone(zone).format(timeFormatter)

fun LocalDate.displayName(): String = when (this) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> format(dayFormatter)
}

/** 1250 → "1.250" */
fun Int.withThousands(): String = NumberFormat.getIntegerInstance(es).format(this)

/** 1.25 → "1,3" con [decimales] decimales */
fun Double.toFixed(digits: Int = 1): String = String.format(es, "%.${digits}f", this)

/** 3725000 ms → "1:02:05"; 125000 → "02:05" */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(es, "%d:%02d:%02d", h, m, s) else String.format(es, "%02d:%02d", m, s)
}
