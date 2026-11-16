package co.edu.ucc.hidrasmart.remote

import android.content.Context
import co.edu.ucc.hidrasmart.ai.CloudAnswer
import co.edu.ucc.hidrasmart.ai.CloudCoach
import co.edu.ucc.hidrasmart.ai.CoachContextBuilder
import co.edu.ucc.hidrasmart.ai.CoachInput
import co.edu.ucc.hidrasmart.ai.WaterPlan
import co.edu.ucc.hidrasmart.domain.Weather

/** Coach en la nube a través del servidor hidrasmart-api. */
class ServerCoach(private val api: HidraSmartApi) : CloudCoach {
    override val isConfigured: Boolean get() = api.isConfigured

    override suspend fun ask(question: String?, input: CoachInput, plan: WaterPlan?, compact: Boolean): CloudAnswer =
        api.coach(CoachContextBuilder.requestBody(question, compact, input, plan))
}

/**
 * Servicios en la nube de HidraSmart (coach y clima). [create] devuelve null si no hay API_BASE_URL:
 * entonces la app funciona solo con su IA local y el clima manual.
 */
class CloudServices private constructor(private val api: HidraSmartApi) {

    val coach: CloudCoach = ServerCoach(api)

    suspend fun weather(city: String): Weather = api.weather(city)

    companion object {
        fun create(context: Context, apiBaseUrl: String, firebase: FirebaseConfig): CloudServices? {
            if (apiBaseUrl.isBlank()) return null
            val app = context.applicationContext
            val appCheck = FirebaseAppCheckProvider.get(app, firebase)
            return CloudServices(HidraSmartApi(apiBaseUrl, InstallId.get(app), appCheck))
        }
    }
}
