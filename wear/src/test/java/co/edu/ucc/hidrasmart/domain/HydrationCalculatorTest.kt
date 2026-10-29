package co.edu.ucc.hidrasmart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationCalculatorTest {

    private val sweatTest = SweatTest(initialWeightKg = 70.0, finalWeightKg = 69.2, fluidMl = 500, urineMl = 0, durationMin = 60)

    @Test
    fun sweatRate_nataExample() {
        // (70 − 69,2) + 0,5 − 0 = 1,3 L en 1 h
        assertEquals(1.3, HydrationCalculator.sweatRate(sweatTest), 0.0001)
    }

    @Test
    fun sweatRate_subtractsUrine() {
        val withUrine = sweatTest.copy(urineMl = 300, durationMin = 30)
        // (0,8 + 0,5 − 0,3) / 0,5 h = 2,0 L/h
        assertEquals(2.0, HydrationCalculator.sweatRate(withUrine), 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sweatRate_rejectsImpossibleData() {
        HydrationCalculator.sweatRate(sweatTest.copy(finalWeightKg = 75.0, fluidMl = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sweatRate_rejectsZeroDuration() {
        HydrationCalculator.sweatRate(sweatTest.copy(durationMin = 0))
    }

    @Test
    fun weightLoss_inPercent() {
        assertEquals(1.142857, HydrationCalculator.weightLossPercent(sweatTest), 0.0001)
    }

    @Test
    fun mlPerAlert_withoutAdjustment() {
        // 1,3 L/h × 1 × 1000 × 15/60 = 325 → 330 (redondeo a 10 ml)
        assertEquals(330, HydrationCalculator.mlPerAlert(1.3, 1.0, 15))
    }

    @Test
    fun mlPerAlert_withFactor() {
        // 0,8 L/h × 1,2 × 1000 × 20/60 = 320
        assertEquals(320, HydrationCalculator.mlPerAlert(0.8, 1.2, 20))
    }

    @Test
    fun factor_isOneInSameConditions() {
        val c = Conditions(altitudeM = 2527.0, temperatureC = 14.0, humidityPct = 75.0, heartRateBpm = 120)
        assertEquals(1.0, HydrationCalculator.factor(c, c, age = 22).value, 0.0001)
    }

    @Test
    fun factor_notTriggeredJustByBeingInPasto() {
        // La prueba se hizo en Pasto y se entrena en Pasto: la altitud alta no suma
        val pastoConditions = Conditions(altitudeM = 2527.0, temperatureC = 14.0, humidityPct = 60.0)
        assertEquals(1.0, HydrationCalculator.factor(pastoConditions, pastoConditions, age = 22).value, 0.0001)
    }

    @Test
    fun factor_addsPerConditionAndIsCapped() {
        val testConds = Conditions(altitudeM = 500.0, temperatureC = 15.0, humidityPct = 50.0)
        val harsh = Conditions(altitudeM = 2600.0, temperatureC = 25.0, humidityPct = 85.0, heartRateBpm = 190)
        val f = HydrationCalculator.factor(harsh, testConds, age = 22)
        assertEquals(1.3, f.value, 0.0001) // 4 razones pero tope 1,3
        assertEquals(4, f.reasons.size)
    }

    @Test
    fun factor_humidity() {
        val dryTest = Conditions(humidityPct = 60.0)
        val f = HydrationCalculator.factor(Conditions(humidityPct = 80.0), dryTest, age = 22)
        assertEquals(1.1, f.value, 0.0001)
        assertTrue(f.reasons.first().contains("Humedad"))
    }

    @Test
    fun maxHeartRate_classic() {
        assertEquals(198, HydrationCalculator.maxHeartRate(22))
    }
}
