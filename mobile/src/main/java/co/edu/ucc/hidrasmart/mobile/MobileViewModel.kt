package co.edu.ucc.hidrasmart.mobile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.ucc.hidrasmart.ai.AiCoach
import co.edu.ucc.hidrasmart.ai.CoachInput
import co.edu.ucc.hidrasmart.ai.HydrationInsights
import co.edu.ucc.hidrasmart.ai.HydrationPredictor
import co.edu.ucc.hidrasmart.ai.Insight
import co.edu.ucc.hidrasmart.ai.WaterPlan
import co.edu.ucc.hidrasmart.data.HydrationRepository
import co.edu.ucc.hidrasmart.domain.Conditions
import co.edu.ucc.hidrasmart.domain.HydrationCalculator
import co.edu.ucc.hidrasmart.domain.Intake
import co.edu.ucc.hidrasmart.domain.Profile
import co.edu.ucc.hidrasmart.domain.SavedSession
import co.edu.ucc.hidrasmart.domain.SweatTest
import co.edu.ucc.hidrasmart.format.toLocalDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Un mensaje del chat con el coach. */
data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val fromCloud: Boolean = false,
    val note: String? = null,
)

/**
 * Estado de la app del celular. Usa el mismo repositorio, calculadora e IA que el reloj
 * (módulo :core); los datos se guardan en el teléfono.
 */
class MobileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HydrationRepository(app)

    // Sin claves en la app: mientras no exista el cliente de hidrasmart-api (Bloque 2),
    // el coach responde con la IA del teléfono (predictor + sistema experto), que no necesita internet.
    private val coach = AiCoach(null)

    init {
        // Versiones anteriores guardaban aquí una clave de Gemini en texto plano: se borra del teléfono
        app.deleteSharedPreferences(LEGACY_SETTINGS)
    }

    var profile by mutableStateOf(repo.profile())
        private set
    var intakes by mutableStateOf(repo.intakes())
        private set
    var sessions by mutableStateOf(repo.sessions())
        private set

    val messages = mutableStateListOf<ChatMessage>()
    var thinking by mutableStateOf(false)
        private set

    /** true si el coach puede usar la IA en la nube (servidor hidrasmart-api); si no, responde la IA local. */
    val cloudAiEnabled: Boolean get() = coach.cloudEnabled

    val todayTotalMl: Int
        get() {
            val today = LocalDate.now()
            return intakes.filter { it.timestamp.toLocalDate() == today }.sumOf { it.ml }
        }

    private fun reload() {
        profile = repo.profile()
        intakes = repo.intakes()
        sessions = repo.sessions()
    }

    // ---------------- Tomas de agua ----------------

    fun logIntake(ml: Int) {
        if (ml <= 0) return
        repo.addIntake(Intake(System.currentTimeMillis(), ml))
        reload()
    }

    // ---------------- Perfil ----------------

    fun saveProfile(p: Profile) {
        repo.saveProfile(p)
        reload()
    }

    /** Prueba de sudoración (NATA 2017); las condiciones son las manuales del perfil. */
    fun saveSweatTest(test: SweatTest): Result<Double> = runCatching {
        val rate = HydrationCalculator.sweatRate(test)
        val conditions = Conditions(
            altitudeM = profile.usualAltitudeM,
            temperatureC = profile.manualTemperatureC,
            humidityPct = profile.manualHumidityPct,
        )
        saveProfile(profile.copy(sweatRateLh = rate, testConditions = conditions, weightKg = test.initialWeightKg))
        rate
    }

    // ---------------- Entrenamientos (registro manual desde el celular) ----------------

    /**
     * Registra un entrenamiento hecho sin el reloj. Sirve de dato de entrenamiento para el
     * modelo de IA. Si hay prueba de sudoración, calcula lo recomendado con la tasa del atleta.
     */
    fun addWorkout(type: String, minutes: Int, drankMl: Int, avgHeartRate: Int?) {
        require(minutes > 0) { "La duración debe ser mayor que 0" }
        val end = System.currentTimeMillis()
        val recommended = profile.sweatRateLh?.let { rate -> ((rate * 1000 * minutes / 60.0) / 10).roundToInt() * 10 } ?: 0
        repo.saveSession(
            SavedSession(
                id = end,
                type = type,
                startTime = end - minutes * 60_000L,
                endTime = end,
                durationSec = minutes * 60L,
                avgHeartRate = avgHeartRate,
                drankMl = drankMl.coerceAtLeast(0),
                recommendedMl = recommended,
            ),
        )
        reload()
    }

    // ---------------- Inteligencia artificial ----------------

    fun coachInput(): CoachInput = CoachInput(
        profile = profile,
        intakes = intakes,
        sessions = sessions,
        now = LocalDateTime.now(),
        zone = ZoneId.systemDefault(),
    )

    fun insights(): List<Insight> = HydrationInsights.analyze(coachInput())

    fun waterPlan(type: String?, minutes: Int): WaterPlan = HydrationPredictor.plan(type, minutes, profile, sessions)

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || thinking) return
        messages += ChatMessage(text, fromUser = true)
        thinking = true
        viewModelScope.launch {
            try {
                val answer = coach.ask(text, coachInput(), compact = false)
                messages += ChatMessage(answer.text, fromUser = false, fromCloud = answer.fromCloud, note = answer.note)
            } finally {
                thinking = false
            }
        }
    }

    fun clearChat() {
        messages.clear()
    }

    companion object {
        private const val LEGACY_SETTINGS = "hidrasmart_settings"
        val WORKOUT_TYPES = listOf("Correr", "Ciclismo", "Gimnasio", "Otro")
    }
}
