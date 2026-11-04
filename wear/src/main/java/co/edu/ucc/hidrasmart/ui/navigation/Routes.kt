package co.edu.ucc.hidrasmart.ui.navigation

import co.edu.ucc.hidrasmart.ui.screens.ProfileField

/** Navigation routes (see the hierarchy diagram in the documentation). */
object Routes {
    const val HOME = "home"
    const val LOG_INTAKE = "log_intake"
    const val CUSTOM_AMOUNT = "custom_amount"
    const val CONFIRMATION = "confirmation/{ml}"
    const val WORKOUT_TYPE = "workout_type"
    const val SESSION = "session"
    const val ALERT = "alert"
    const val CONFIRM_END = "confirm_end"
    const val SUMMARY = "summary/{id}"
    const val HISTORY = "history"
    const val DAY = "day/{date}"
    const val SESSION_DETAIL = "session_detail/{id}"
    const val PROFILE = "profile"
    const val EDIT_FIELD = "edit_field/{field}"
    const val SWEAT_TEST = "sweat_test"
    const val COACH = "coach"
    const val PLAN = "plan"

    fun confirmation(ml: Int) = "confirmation/$ml"
    fun summary(id: Long) = "summary/$id"
    fun day(date: String) = "day/$date"
    fun sessionDetail(id: Long) = "session_detail/$id"
    fun editField(field: ProfileField) = "edit_field/${field.name}"
}
