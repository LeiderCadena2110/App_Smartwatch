package co.edu.ucc.hidrasmart.remote

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Datos públicos del proyecto de Firebase (no son secretos: identifican el proyecto). */
data class FirebaseConfig(val appId: String, val apiKey: String, val projectId: String) {
    val isComplete: Boolean get() = appId.isNotBlank() && apiKey.isNotBlank() && projectId.isNotBlank()
}

/**
 * Tokens de Firebase App Check. Release: Play Integrity (app original instalada desde Google Play en un
 * dispositivo íntegro). Debug: proveedor de depuración, cuyo secreto se registra en la consola de Firebase.
 *
 * Firebase se inicia a mano con [FirebaseConfig] (BuildConfig), sin google-services.json: así el proyecto
 * compila aunque Firebase aún no esté configurado.
 */
class FirebaseAppCheckProvider private constructor(private val appCheck: FirebaseAppCheck) : AppCheckTokenProvider {

    override suspend fun token(): String? = try {
        // Firebase guarda el token en caché y lo renueva solo; false = no forzar uno nuevo
        withTimeoutOrNull(TOKEN_TIMEOUT_MS) { appCheck.getAppCheckToken(false).awaitResult().token }
    } catch (e: CancellationException) {
        throw e // la pantalla se cerró
    } catch (e: Exception) {
        null // sin token: el servidor responde 401 y la app usa su IA local
    }

    companion object {
        private const val APP_NAME = "hidrasmart"
        private const val TOKEN_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: FirebaseAppCheckProvider? = null

        /** Devuelve el proveedor (uno por proceso), o null si falta la configuración de Firebase. */
        fun get(context: Context, config: FirebaseConfig): FirebaseAppCheckProvider? {
            if (!config.isComplete) return null
            instance?.let { return it }
            return synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val firebase = FirebaseApp.getApps(app).firstOrNull { it.name == APP_NAME }
                        ?: FirebaseApp.initializeApp(
                            app,
                            FirebaseOptions.Builder()
                                .setApplicationId(config.appId)
                                .setApiKey(config.apiKey)
                                .setProjectId(config.projectId)
                                .build(),
                            APP_NAME,
                        )
                    val appCheck = FirebaseAppCheck.getInstance(firebase)
                    appCheck.installAppCheckProviderFactory(AppCheckFactories.providerFactory())
                    FirebaseAppCheckProvider(appCheck).also { instance = it }
                }
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
