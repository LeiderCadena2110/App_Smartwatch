package co.edu.ucc.hidrasmart.ai

/** Utilidades de texto del coach. El prompt ya no vive en la app: lo arma el servidor hidrasmart-api. */
object CoachText {

    /** Recorta la respuesta para la pantalla del reloj sin cortar palabras. */
    fun fitForWatch(text: String, maxChars: Int = 240): String {
        val clean = text.replace(Regex("[*#_`]"), "").replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf(' '))
        return (if (end > maxChars / 2) cut.take(end + 1) else cut).trimEnd() + "…"
    }
}
