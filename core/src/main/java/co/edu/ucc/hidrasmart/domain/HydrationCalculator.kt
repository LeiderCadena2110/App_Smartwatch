package co.edu.ucc.hidrasmart.domain

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
object HydrationCalculator {

    const val MAX_FACTOR = 1.3
    private const val FACTOR_STEP = 0.1
    private val spanishLocale = Locale.forLanguageTag("es-CO")

    /** Tasa de sudoración en litros por hora. Lanza IllegalArgumentException si los datos no tienen sentido. */
    fun sweatRate(p: SweatTest): Double {
        require(p.durationMin > 0) { "La duración debe ser mayor que 0" }
        require(p.initialWeightKg > 0 && p.finalWeightKg > 0) { "Los pesos deben ser mayores que 0" }
        require(p.fluidMl >= 0 && p.urineMl >= 0) { "Líquido y orina no pueden ser negativos" }
        val lossLiters = (p.initialWeightKg - p.finalWeightKg) + p.fluidMl / 1000.0 - p.urineMl / 1000.0
        val rate = lossLiters / (p.durationMin / 60.0)
        require(rate in 0.05..4.0) {
            "Resultado fuera de rango (${String.format(spanishLocale, "%.2f", rate)} L/h): revisa los datos"
        }
        return rate
    }

    /** Porcentaje del peso perdido en la prueba. NATA recomienda que sea menor al 2 %. */
    fun weightLossPercent(p: SweatTest): Double =
        (p.initialWeightKg - p.finalWeightKg) / p.initialWeightKg * 100.0

    /** Frecuencia cardíaca máxima estimada (fórmula clásica 220 − edad). */
    fun maxHeartRate(age: Int): Int = 220 - age

    fun factor(current: Conditions, sweatTest: Conditions?, age: Int): Factor {
        val reasons = mutableListOf<String>()
        if (sweatTest != null) {
            val currentAltitude = current.altitudeM
            val testAltitude = sweatTest.altitudeM
            if (currentAltitude != null && testAltitude != null && currentAltitude > testAltitude + 500) reasons += "Más de 500 m sobre la prueba"

            val currentHumidity = current.humidityPct
            val testHumidity = sweatTest.humidityPct
            if (currentHumidity != null && currentHumidity > 70 && (testHumidity == null || testHumidity <= 70)) reasons += "Humedad mayor al 70 %"

            val currentTemp = current.temperatureC
            val testTemp = sweatTest.temperatureC
            if (currentTemp != null && testTemp != null && currentTemp > testTemp + 5) reasons += "Más de 5 °C sobre la prueba"
        }
        val heartRate = current.heartRateBpm
        if (heartRate != null && heartRate > 0.85 * maxHeartRate(age)) reasons += "Frecuencia cardíaca alta"

        val value = min(1.0 + FACTOR_STEP * reasons.size, MAX_FACTOR)
        return Factor(value, reasons)
    }

    /**
     * Mililitros a tomar en cada alerta = tasa × f × 1000 × (intervalo / 60), redondeado a 10 ml.
     * Tasa × f es lo que se estima que el atleta suda hoy; nunca se recomienda más que eso.
     */
    fun mlPerAlert(rateLh: Double, factor: Double, intervalMin: Int): Int {
        require(intervalMin > 0) { "El intervalo debe ser mayor que 0" }
        val ml = rateLh * factor * 1000.0 * (intervalMin / 60.0)
        return ((ml / 10.0).roundToInt() * 10)
    }
}
