package co.edu.ucc.hidrasmart.ui.navigation

import co.edu.ucc.hidrasmart.ui.screens.ProfileField

/** Navigation routes (see the hierarchy diagram in the documentation). */
object Routes {
    const val HOME = "inicio"
    const val LOG_INTAKE = "registrar"
    const val CUSTOM_AMOUNT = "otra_cantidad"
    const val CONFIRMATION = "confirmacion/{ml}"
    const val WORKOUT_TYPE = "tipo"
    const val SESSION = "sesion"
    const val ALERT = "alerta"
    const val CONFIRM_END = "confirmar_fin"
    const val SUMMARY = "resumen/{id}"
    const val HISTORY = "historial"
    const val DAY = "dia/{fecha}"
    const val SESSION_DETAIL = "detalle_sesion/{id}"
    const val PROFILE = "perfil"
    const val EDIT_FIELD = "editar/{campo}"
    const val SWEAT_TEST = "prueba"
    const val COACH = "coach"
    const val PLAN = "plan"

    fun confirmation(ml: Int) = "confirmacion/$ml"
    fun summary(id: Long) = "resumen/$id"
    fun day(date: String) = "dia/$date"
    fun sessionDetail(id: Long) = "detalle_sesion/$id"
    fun editField(field: ProfileField) = "editar/${field.name}"
}
