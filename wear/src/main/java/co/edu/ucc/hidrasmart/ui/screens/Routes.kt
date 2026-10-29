package co.edu.ucc.hidrasmart.ui.screens

/** Rutas de navegación (ver el diagrama de jerarquía de la documentación). */
object Rutas {
    const val INICIO = "inicio"
    const val REGISTRAR = "registrar"
    const val OTRA_CANTIDAD = "otra_cantidad"
    const val CONFIRMACION = "confirmacion/{ml}"
    const val TIPO = "tipo"
    const val SESION = "sesion"
    const val ALERT = "alerta"
    const val CONFIRMAR_FIN = "confirmar_fin"
    const val RESUMEN = "resumen/{id}"
    const val HISTORIAL = "historial"
    const val DIA = "dia/{fecha}"
    const val DETALLE_SESION = "detalle_sesion/{id}"
    const val PROFILE = "perfil"
    const val EDITAR = "editar/{campo}"
    const val PRUEBA = "prueba"

    fun confirmacion(ml: Int) = "confirmacion/$ml"
    fun resumen(id: Long) = "resumen/$id"
    fun dia(fecha: String) = "dia/$fecha"
    fun detalleSesion(id: Long) = "detalle_sesion/$id"
    fun editar(campo: CampoPerfil) = "editar/${campo.name}"
}
