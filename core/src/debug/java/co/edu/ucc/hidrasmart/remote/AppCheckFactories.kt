package co.edu.ucc.hidrasmart.remote

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Versión de depuración: proveedor de depuración de App Check (sirve en el emulador).
 * Al primer uso, Logcat muestra "Enter this debug secret into the allow list in the Firebase Console…":
 * ese secreto se registra en Firebase › App Check › Administrar tokens de depuración.
 */
internal object AppCheckFactories {
    fun providerFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
