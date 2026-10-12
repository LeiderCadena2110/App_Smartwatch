package co.edu.ucc.hidrasmart.dominio

import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cálculo de hidratación basado en la guía de la NATA (2017):
 *  - Tasa de sudoración = (peso inicial − peso final + líquido ingerido − orina) / duración.
 *  - No recomendar más líquido del que se pierde.
 *  - ~200 ml cada 15–20 min como referencia de frecuencia (intervalo por defecto: 15 min).
 *
 * El factor f (1,0 a 1,3) es un ajuste propio del proyecto, NO viene de NATA ni ACSM:
 * sube 0,1 por cada condición más exigente que las de la prueba de sudoración.
 */
object CalculadoraHidratacion {

    const val FACTOR_MAXIMO = 1.3
    private const val PASO_FACTOR = 0.1
    private val es = Locale.forLanguageTag("es-CO")

    /** Tasa de sudoración en litros por hora. Lanza IllegalArgumentException si los datos no tienen sentido. */
    fun tasaSudoracion(p: PruebaSudoracion): Double {
        require(p.duracionMin > 0) { "La duración debe ser mayor que 0" }
        require(p.pesoInicialKg > 0 && p.pesoFinalKg > 0) { "Los pesos deben ser mayores que 0" }
        require(p.liquidoMl >= 0 && p.orinaMl >= 0) { "Líquido y orina no pueden ser negativos" }
        val perdidaL = (p.pesoInicialKg - p.pesoFinalKg) + p.liquidoMl / 1000.0 - p.orinaMl / 1000.0
        val tasa = perdidaL / (p.duracionMin / 60.0)
        require(tasa in 0.05..4.0) {
            "Resultado fuera de rango (${String.format(es, "%.2f", tasa)} L/h): revisa los datos"
        }
        return tasa
    }

    /** Porcentaje del peso perdido en la prueba. NATA recomienda que sea menor al 2 %. */
    fun porcentajePerdidaPeso(p: PruebaSudoracion): Double =
        (p.pesoInicialKg - p.pesoFinalKg) / p.pesoInicialKg * 100.0

    /** Frecuencia cardíaca máxima estimada (fórmula clásica 220 − edad). */
    fun fcMaxima(edad: Int): Int = 220 - edad

    fun factor(actual: Condiciones, prueba: Condiciones?, edad: Int): Factor {
        val razones = mutableListOf<String>()
        if (prueba != null) {
            val altAct = actual.altitudM
            val altPru = prueba.altitudM
            if (altAct != null && altPru != null && altAct > altPru + 500) razones += "Más de 500 m sobre la prueba"

            val humAct = actual.humedadPct
            val humPru = prueba.humedadPct
            if (humAct != null && humAct > 70 && (humPru == null || humPru <= 70)) razones += "Humedad mayor al 70 %"

            val tAct = actual.temperaturaC
            val tPru = prueba.temperaturaC
            if (tAct != null && tPru != null && tAct > tPru + 5) razones += "Más de 5 °C sobre la prueba"
        }
        val fc = actual.fcBpm
        if (fc != null && fc > 0.85 * fcMaxima(edad)) razones += "Frecuencia cardíaca alta"

        val valor = min(1.0 + PASO_FACTOR * razones.size, FACTOR_MAXIMO)
        return Factor(valor, razones)
    }

    /**
     * Mililitros a tomar en cada alerta = tasa × f × 1000 × (intervalo / 60), redondeado a 10 ml.
     * Tasa × f es lo que se estima que el atleta suda hoy; nunca se recomienda más que eso.
     */
    fun mlPorAlerta(tasaLh: Double, factor: Double, intervaloMin: Int): Int {
        require(intervaloMin > 0) { "El intervalo debe ser mayor que 0" }
        val ml = tasaLh * factor * 1000.0 * (intervaloMin / 60.0)
        return ((ml / 10.0).roundToInt() * 10)
    }
}
