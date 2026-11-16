package co.edu.ucc.hidrasmart.ai

import co.edu.ucc.hidrasmart.ai.CloudCoachException.Kind
import co.edu.ucc.hidrasmart.domain.Intake
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import co.edu.ucc.hidrasmart.remote.HidraSmartApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato con el servidor hidrasmart-api (server/src/schemas.ts) y respaldo local del coach. */
class CloudCoachTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    private fun intake(hour: Int, ml: Int) = Intake(today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), ml)

    private fun session(type: String, minutes: Long, drank: Int, recommended: Int, hr: Int? = 150) = SavedSession(
        id = minutes,
        type = type,
        startTime = today.minusDays(minutes % 5).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
        endTime = 0,
        durationSec = minutes * 60,
        avgHeartRate = hr,
        drankMl = drank,
        recommendedMl = recommended,
    )

    private fun input(profile: Profile = Profile(dailyGoalMl = 2500, sweatRateLh = 0.9, weightKg = 72.4, age = 24)) = CoachInput(
        profile = profile,
        intakes = listOf(intake(8, 500), intake(11, 400)),
        sessions = listOf(session("Correr", 45, 400, 600)),
        now = LocalDateTime.of(today, LocalTime.of(14, 30)),
        zone = zone,
        conditionsFactor = 1.1,
    )

    // ---------- Cuerpo de la petición ----------

    @Test
    fun requestBody_hasEveryFieldTheServerRequires() {
        val body = JSONObject(CoachContextBuilder.requestBody("¿Cuánta agua llevo?", true, input(), null))
        assertEquals("¿Cuánta agua llevo?", body.getString("question"))
        assertTrue(body.getBoolean("compact"))
        val c = body.getJSONObject("context")
        val required = listOf(
            "localTime", "age", "weightKg", "dailyGoalMl", "todayMl", "expectedByNowMl", "weeklyAverageMl",
            "goalStreakDays", "sweatRateLh", "conditionsFactor", "inWorkout", "adherencePct", "recentSessions",
            "plan", "insights",
        )
        required.forEach { assertTrue("falta $it", c.has(it)) }
        assertEquals(required.size, c.length()) // el servidor rechaza campos de más
        assertEquals("14:30", c.getString("localTime"))
        assertEquals(900, c.getInt("todayMl"))
        assertEquals(2500, c.getInt("dailyGoalMl"))
        assertEquals(67, c.getInt("adherencePct"))
        assertTrue(c.isNull("plan"))
        val s = c.getJSONArray("recentSessions").getJSONObject(0)
        assertEquals("Correr", s.getString("type"))
        assertEquals(45, s.getInt("minutes"))
    }

    @Test
    fun requestBody_emptyQuestionIsNullAndLongQuestionIsCut() {
        assertTrue(JSONObject(CoachContextBuilder.requestBody("   ", false, input(), null)).isNull("question"))
        assertTrue(JSONObject(CoachContextBuilder.requestBody(null, false, input(), null)).isNull("question"))
        val long = JSONObject(CoachContextBuilder.requestBody("a".repeat(500), false, input(), null)).getString("question")
        assertEquals(CoachContextBuilder.MAX_QUESTION, long.length)
    }

    @Test
    fun context_clampsValuesToTheServerRanges() {
        val odd = input(Profile(dailyGoalMl = 20_000, age = 5, weightKg = 400.0, sweatRateLh = 9.0)).copy(
            intakes = listOf(intake(9, 50_000)),
            sessions = listOf(session("x".repeat(80), 900, 50_000, 0, hr = 300)),
            conditionsFactor = 5.0,
        )
        val c = CoachContextBuilder.context(odd, null)
        assertEquals(8000, c.getInt("dailyGoalMl"))
        assertEquals(10, c.getInt("age"))
        assertEquals(250.0, c.getDouble("weightKg"), 0.0)
        assertEquals(4.0, c.getDouble("sweatRateLh"), 0.0)
        assertEquals(2.0, c.getDouble("conditionsFactor"), 0.0)
        assertEquals(20_000, c.getInt("todayMl"))
        val s = c.getJSONArray("recentSessions").getJSONObject(0)
        assertEquals(30, s.getString("type").length)
        assertEquals(600, s.getInt("minutes"))
        assertEquals(230, s.getInt("avgHeartRate"))
        assertEquals(10_000, s.getInt("drankMl"))
    }

    @Test
    fun context_sendsAtMostFiveSessionsAndSixInsights() {
        val many = input().copy(sessions = (1L..9L).map { session("Correr", 30 + it, 300, 400) })
        val c = CoachContextBuilder.context(many, null)
        assertEquals(5, c.getJSONArray("recentSessions").length())
        assertTrue(c.getJSONArray("insights").length() <= 6)
    }

    // ---------- Respuestas del servidor ----------

    @Test
    fun api_mapsStatusCodesToErrorKinds() {
        assertEquals(Kind.INVALID_REQUEST, HidraSmartApi.kindFor(400))
        assertEquals(Kind.UNAUTHORIZED, HidraSmartApi.kindFor(401))
        assertEquals(Kind.INVALID_REQUEST, HidraSmartApi.kindFor(413))
        assertEquals(Kind.BLOCKED, HidraSmartApi.kindFor(422))
        assertEquals(Kind.QUOTA_EXCEEDED, HidraSmartApi.kindFor(429))
        assertEquals(Kind.UNAVAILABLE, HidraSmartApi.kindFor(503))
        assertEquals(Kind.UNAVAILABLE, HidraSmartApi.kindFor(500))
    }

    @Test
    fun api_parsesCoachAnswerAndErrors() {
        val answer = HidraSmartApi.parseCoach(
            """{"text":" Toma 250 ml. ","source":"gemini","model":"gemini-3.5-flash-lite","promptVersion":"2026-11-16.1","cached":true}""",
        )
        assertEquals("Toma 250 ml.", answer.text)
        assertEquals("2026-11-16.1", answer.promptVersion)
        assertTrue(answer.cached)
        assertEquals("quota_exceeded", HidraSmartApi.errorCode("""{"error":"quota_exceeded","message":"x"}"""))
        assertNull(HidraSmartApi.errorCode("<html>"))
        val empty = runCatching { HidraSmartApi.parseCoach("""{"text":""}""") }.exceptionOrNull()
        assertEquals(Kind.UNAVAILABLE, (empty as CloudCoachException).kind)
    }

    @Test
    fun api_parsesWeather() {
        val w = HidraSmartApi.parseWeather("""{"city":"Pasto,CO","temperatureC":13.5,"humidityPct":81,"fetchedAt":"2026-11-16T05:00:00Z"}""")
        assertEquals(13.5, w.temperatureC, 0.0)
        assertEquals(81.0, w.humidityPct, 0.0)
        assertEquals("Pasto,CO", w.city)
    }

    // ---------- AiCoach con la nube y respaldo local ----------

    private class FakeCloud(private val result: () -> CloudAnswer) : CloudCoach {
        override val isConfigured = true
        override suspend fun ask(question: String?, input: CoachInput, plan: WaterPlan?, compact: Boolean) = result()
    }

    @Test
    fun aiCoach_usesTheCloudAnswer() = runBlocking {
        val coach = AiCoach(FakeCloud { CloudAnswer("**Toma** 300 ml ahora.", "m", "v", false) })
        val answer = coach.ask("¿Qué hago?", input(), compact = true)
        assertTrue(answer.fromCloud)
        assertEquals("Toma 300 ml ahora.", answer.text)
        assertNull(answer.note)
    }

    @Test
    fun aiCoach_fallsBackToLocalWithAReasonForEachError() = runBlocking {
        for (kind in Kind.values()) {
            val coach = AiCoach(FakeCloud { throw CloudCoachException(kind, "falla") })
            val answer = coach.ask("¿Cómo voy hoy?", input(), compact = false)
            assertFalse(answer.fromCloud)
            assertTrue(answer.text.isNotBlank())
            assertEquals(AiCoach.noteFor(kind), answer.note)
        }
        assertEquals("Límite diario del coach en la nube: respuesta local", AiCoach.noteFor(Kind.QUOTA_EXCEEDED))
    }

    @Test
    fun aiCoach_withoutCloudAnswersLocally() = runBlocking {
        val coach = AiCoach(null)
        assertFalse(coach.cloudEnabled)
        val answer = coach.ask(null, input(), compact = true)
        assertFalse(answer.fromCloud)
        assertNull(answer.note)
    }
}
