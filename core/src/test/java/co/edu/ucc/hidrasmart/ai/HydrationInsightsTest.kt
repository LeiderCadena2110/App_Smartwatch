package co.edu.ucc.hidrasmart.ai

import co.edu.ucc.hidrasmart.domain.Intake
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationInsightsTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 28)
    private val profile = Profile(dailyGoalMl = 2000, sweatRateLh = 1.0)

    private fun intake(day: LocalDate, hour: Int, ml: Int) =
        Intake(day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), ml)

    private fun input(time: LocalTime, intakes: List<Intake>, sessions: List<SavedSession> = emptyList(), p: Profile = profile) =
        CoachInput(p, intakes, sessions, LocalDateTime.of(today, time), zone)

    @Test
    fun expectedByNow_isLinearDuringTheDay() {
        assertEquals(0, HydrationInsights.expectedByNow(2000, LocalTime.of(6, 0)))
        assertEquals(1000, HydrationInsights.expectedByNow(2000, LocalTime.of(14, 0)))
        assertEquals(2000, HydrationInsights.expectedByNow(2000, LocalTime.of(22, 0)))
    }

    @Test
    fun analyze_flagsHighRiskWhenFarBehind() {
        val result = HydrationInsights.analyze(input(LocalTime.of(14, 0), listOf(intake(today, 8, 250))))
        assertEquals("Vas atrasado", result.first().title)
        assertEquals(RiskLevel.HIGH, HydrationInsights.overallRisk(result))
    }

    @Test
    fun analyze_congratulatesWhenGoalMet() {
        val result = HydrationInsights.analyze(input(LocalTime.of(14, 0), listOf(intake(today, 9, 2100))))
        assertTrue(result.any { it.title == "Meta cumplida" })
        assertEquals(RiskLevel.LOW, HydrationInsights.overallRisk(result))
    }

    @Test
    fun analyze_suggestsSweatTestWhenMissing() {
        val result = HydrationInsights.analyze(input(LocalTime.of(8, 0), emptyList(), p = profile.copy(sweatRateLh = null)))
        assertTrue(result.any { it.title == "Personaliza tus alertas" })
    }

    @Test
    fun adherence_usesOnlySessionsWithRecommendation() {
        val sessions = listOf(
            SavedSession(1, "Correr", 1, 2, 3600, 150, drankMl = 300, recommendedMl = 600),
            SavedSession(2, "Correr", 2, 3, 3600, 150, drankMl = 500, recommendedMl = 0),
        )
        assertEquals(0.5, HydrationInsights.adherence(sessions)!!, 1e-9)
        assertNull(HydrationInsights.adherence(emptyList()))
    }

    @Test
    fun goalStreak_countsConsecutiveDays() {
        val intakes = listOf(
            intake(today.minusDays(1), 10, 2000),
            intake(today.minusDays(2), 10, 2500),
            intake(today.minusDays(4), 10, 2500), // el día 3 se rompió la racha
            intake(today, 10, 500),               // hoy aún no cumple: no cuenta, pero no rompe
        )
        assertEquals(2, HydrationInsights.goalStreak(intakes, 2000, today, zone))
    }

    @Test
    fun localCoach_answersStatusAndPlanQuestions() {
        val inp = input(LocalTime.of(14, 0), listOf(intake(today, 8, 500)))
        val plan = HydrationPredictor.plan(null, 60, profile, emptyList())
        val status = LocalCoach.answer("¿Cómo voy hoy?", inp, plan)
        assertTrue(status, status.startsWith("Llevas 500 de 2000 ml"))
        val bottle = LocalCoach.answer("¿Cuánta agua llevo para entrenar 1 hora?", inp, plan)
        assertTrue(bottle, bottle.contains("1000 ml"))
    }

    @Test
    fun localCoach_normalizesAccents() {
        assertEquals("cuanta agua llevo", LocalCoach.normalize("¿Cuánta agua llevo?"))
    }

    @Test
    fun prompt_escapesJsonAndIncludesContext() {
        val json = PromptBuilder.requestJson("sys \"x\"", "línea 1\nlínea 2", 100, 0.5)
        assertTrue(json, json.contains("sys \\\"x\\\""))
        assertTrue(json, json.contains("línea 1\\nlínea 2"))
        assertTrue(json, json.contains("\"maxOutputTokens\":100"))
        val ctx = PromptBuilder.context(input(LocalTime.of(14, 0), listOf(intake(today, 8, 500))), null)
        assertTrue(ctx, ctx.contains("tomado hoy: 500 ml"))
    }

    @Test
    fun prompt_fitForWatchCutsLongText() {
        val long = "Toma agua. ".repeat(40)
        val fitted = PromptBuilder.fitForWatch(long, 100)
        assertTrue(fitted.length <= 101)
        assertTrue(fitted.endsWith("…"))
        assertEquals("Hola mundo", PromptBuilder.fitForWatch("**Hola**   mundo"))
    }
}
