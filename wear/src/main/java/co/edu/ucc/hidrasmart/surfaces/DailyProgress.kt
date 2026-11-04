package co.edu.ucc.hidrasmart.surfaces

import android.content.Context
import co.edu.ucc.hidrasmart.data.HydrationRepository
import co.edu.ucc.hidrasmart.format.toLocalDate
import java.time.LocalDate

/** Lo tomado hoy frente a la meta. Lo usan la Tile y la complicación, que corren sin la app abierta. */
data class DailyProgress(val totalMl: Int, val goalMl: Int) {

    /** De 0 a 1. */
    val fraction: Float get() = if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f

    val goalMet: Boolean get() = goalMl in 1..totalMl

    companion object {
        fun read(context: Context): DailyProgress {
            val repo = HydrationRepository(context)
            val today = LocalDate.now()
            val total = repo.intakes().filter { it.timestamp.toLocalDate() == today }.sumOf { it.ml }
            return DailyProgress(total, repo.profile().dailyGoalMl)
        }
    }
}
