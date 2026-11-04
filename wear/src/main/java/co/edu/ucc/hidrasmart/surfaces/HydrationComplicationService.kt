package co.edu.ucc.hidrasmart.surfaces

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import co.edu.ucc.hidrasmart.MainActivity
import co.edu.ucc.hidrasmart.R
import co.edu.ucc.hidrasmart.format.toFixed
import co.edu.ucc.hidrasmart.format.withThousands

/**
 * Complicación para la esfera del reloj: lo tomado hoy.
 * - RANGED_VALUE: mini anillo de progreso frente a la meta, con el total.
 * - SHORT_TEXT: solo el total con la gota.
 * Tocarla abre la app.
 */
class HydrationComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(this, request.complicationType, DailyProgress.read(this))

    /** Ejemplo que se muestra al elegir la complicación en el editor de la esfera. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(this, type, DailyProgress(totalMl = 1250, goalMl = 2500))

    private fun build(context: Context, type: ComplicationType, progress: DailyProgress): ComplicationData? {
        val shortText = plain(shortAmount(progress.totalMl))
        val description = plain(
            context.getString(R.string.complication_description, progress.totalMl.withThousands(), progress.goalMl.withThousands()),
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_drop)).build()
        val tap = openApp(context)

        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                val max = progress.goalMl.coerceAtLeast(1).toFloat()
                // Orden: valor, mínimo, máximo, descripción para accesibilidad
                RangedValueComplicationData.Builder(progress.totalMl.toFloat().coerceIn(0f, max), 0f, max, description)
                    .setText(shortText)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            }
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(shortText, description)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    /** Texto corto que cabe en la complicación: "750 ml" o "1,3 L". */
    private fun shortAmount(ml: Int): String =
        if (ml < 1000) "$ml ml" else "${(ml / 1000.0).toFixed(1)} L"

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
