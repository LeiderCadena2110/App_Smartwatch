package co.edu.ucc.hidrasmart.ai

import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationPredictorTest {

    private val profile = Profile(age = 20) // FC máxima 200

    private fun session(type: String, minutes: Int, hr: Int?, recommended: Int, drank: Int = 0, id: Long = minutes.toLong()) =
        SavedSession(id, type, startTime = id, endTime = id + minutes * 60_000L, durationSec = minutes * 60L,
            avgHeartRate = hr, drankMl = drank, recommendedMl = recommended)

    @Test
    fun ridge_recoversExactLine() {
        // y = 2·x1 + 3·x2 sin ruido: con λ casi 0 los pesos deben salir iguales
        val x = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 1.0))
        val y = x.map { 2 * it[0] + 3 * it[1] }
        val w = RidgeRegression.fit(x, y, 1e-9)
        assertEquals(2.0, w[0], 1e-6)
        assertEquals(3.0, w[1], 1e-6)
        assertEquals(1.0, RidgeRegression.rSquared(w, x, y), 1e-9)
    }

    @Test
    fun ridge_shrinksWeightsWithLargeLambda() {
        val x = listOf(doubleArrayOf(1.0), doubleArrayOf(2.0), doubleArrayOf(3.0))
        val y = listOf(10.0, 20.0, 30.0)
        val exact = RidgeRegression.fit(x, y, 0.0)[0]
        val shrunk = RidgeRegression.fit(x, y, 100.0)[0]
        assertEquals(10.0, exact, 1e-9)
        assertTrue(shrunk < exact)
    }

    @Test
    fun train_needsMinimumSamples() {
        val two = listOf(session("Correr", 60, 150, 700), session("Correr", 45, 140, 500, id = 2))
        assertNull(HydrationPredictor.train(two, profile.age))
    }

    @Test
    fun train_ignoresShortAndEmptySessions() {
        val data = listOf(
            session("Correr", 5, 150, 100, id = 1),   // muy corta
            session("Correr", 60, 150, 0, id = 2),    // sin datos de agua
            session("Correr", 60, 150, 700, id = 3),
            session("Correr", 30, 150, 350, id = 4),
        )
        assertNull(HydrationPredictor.train(data, profile.age))
    }

    @Test
    fun model_learnsRateFromIntensity() {
        // Datos generados con tasa = 200 + 800·intensidad (ml/h)
        fun ml(minutes: Int, hr: Int) = ((minutes / 60.0) * (200 + 800 * (hr / 200.0))).toInt()
        val data = listOf(
            session("Correr", 60, 120, ml(60, 120), id = 1),
            session("Correr", 90, 160, ml(90, 160), id = 2),
            session("Correr", 45, 180, ml(45, 180), id = 3),
            session("Correr", 30, 100, ml(30, 100), id = 4),
        )
        val model = HydrationPredictor.train(data, profile.age)
        assertNotNull(model)
        // A intensidad 0,75 (150 lpm): 200 + 600 = 800 ml/h
        assertEquals(800.0, model!!.ratePerHour(0.75), 15.0)
        assertTrue(model.rSquared > 0.99)
    }

    @Test
    fun plan_usesPersonalModelWhenEnoughData() {
        val data = (1..4).map { session("Correr", 60, 150, 800, id = it.toLong()) }
        val plan = HydrationPredictor.plan("Correr", 90, profile, data)
        assertEquals(PlanSource.PERSONAL_MODEL, plan.source)
        assertEquals(4, plan.samples)
        assertEquals(1200, plan.totalMl, 20)
    }

    @Test
    fun plan_fallsBackToSweatTestThenGeneralGuide() {
        val withTest = HydrationPredictor.plan("Correr", 60, profile.copy(sweatRateLh = 1.1), emptyList())
        assertEquals(PlanSource.SWEAT_TEST, withTest.source)
        assertEquals(1100, withTest.totalMl)

        val general = HydrationPredictor.plan("Correr", 30, profile, emptyList())
        assertEquals(PlanSource.GENERAL_GUIDE, general.source)
        assertEquals(300, general.totalMl) // 600 ml/h × 0,5 h
    }

    @Test
    fun plan_usesAllSessionsWhenTypeHasFewSamples() {
        val data = (1..3).map { session("Ciclismo", 60, 150, 900, id = it.toLong()) } + session("Correr", 60, 150, 600, id = 9)
        val plan = HydrationPredictor.plan("Correr", 60, profile, data)
        assertEquals(PlanSource.PERSONAL_MODEL, plan.source)
        assertEquals(4, plan.samples)
    }

    @Test
    fun intensity_defaultsWithoutHeartRate() {
        assertEquals(HydrationPredictor.DEFAULT_INTENSITY, HydrationPredictor.intensity(null, 20), 1e-9)
        assertEquals(0.75, HydrationPredictor.intensity(150, 20), 1e-9)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("expected $expected ± $delta but was $actual", kotlin.math.abs(expected - actual) <= delta)
}
