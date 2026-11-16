package co.edu.ucc.hidrasmart.remote

/** Entrega el token de Firebase App Check para el encabezado X-Firebase-AppCheck; null si no hay. */
fun interface AppCheckTokenProvider {
    suspend fun token(): String?
}
