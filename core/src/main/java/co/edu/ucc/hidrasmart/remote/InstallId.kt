package co.edu.ucc.hidrasmart.remote

import android.content.Context
import java.util.UUID

/**
 * Identificador aleatorio de esta instalación (UUID). Solo sirve para la cuota diaria del coach
 * en el servidor: no identifica a la persona y cambia si se reinstala la app.
 */
object InstallId {
    private const val PREFS = "hidrasmart_install"
    private const val KEY = "install_id"

    @Synchronized
    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
