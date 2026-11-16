package co.edu.ucc.hidrasmart.remote

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Versión de release: Play Integrity (app original de Google Play en un dispositivo íntegro). */
internal object AppCheckFactories {
    fun providerFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
