package co.edu.ucc.hidrasmart.surfaces

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import co.edu.ucc.hidrasmart.MainActivity
import co.edu.ucc.hidrasmart.R
import co.edu.ucc.hidrasmart.alerts.SessionEngine
import co.edu.ucc.hidrasmart.format.withThousands
import com.google.common.util.concurrent.ListenableFuture

/**
 * Tile de HidraSmart (se ve al deslizar la esfera hacia un lado):
 * anillo con lo tomado hoy frente a la meta y botón "+250 ml" que registra sin abrir la app.
 * Tocar el número abre la app.
 */
class HydrationTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        handleClick(requestParams.currentState.lastClickableId)

        val progress = DailyProgress.read(this)
        val layout = layout(this, requestParams.deviceConfiguration, progress)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Se refresca sola cada 15 min (por ejemplo, al empezar un día nuevo)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return immediate(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        immediate(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    /**
     * Cada vez que se dibuja la Tile, el botón recibe un id nuevo ("agregar@hora").
     * Así un mismo toque nunca se cuenta dos veces aunque el sistema repita la petición.
     */
    private fun handleClick(clickedId: String) {
        if (!clickedId.startsWith(ADD_PREFIX)) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_HANDLED, null) == clickedId) return
        prefs.edit().putString(KEY_HANDLED, clickedId).apply()
        // La Tile ya se dibuja con el total nuevo en esta misma respuesta: solo se avisa a la complicación
        SessionEngine.logIntake(this, QUICK_ML, refreshTile = false)
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 15 * 60_000L
        private const val QUICK_ML = 250
        private const val ADD_PREFIX = "agregar@"
        private const val OPEN_APP_ID = "abrir_app"
        private const val PREFS = "tile_hidratacion"
        private const val KEY_HANDLED = "ultimo_toque"

        private val LIGHT_WATER = argb(0xFF4FC3F7.toInt())
        private val GREEN = argb(0xFF66BB6A.toInt())
        private val WHITE = argb(0xFFFFFFFF.toInt())
        private val LIGHT_GRAY = argb(0xFFBDBDBD.toInt())

        private fun <T> immediate(value: T): ListenableFuture<T> =
            CallbackToFutureAdapter.getFuture { completer ->
                completer.set(value)
                "HidraSmartTile"
            }

        fun layout(context: Context, device: DeviceParameters, progress: DailyProgress): LayoutElementBuilders.LayoutElement {
            val ringColor = if (progress.goalMet) 0xFF66BB6A.toInt() else 0xFF4FC3F7.toInt()
            val ring = CircularProgressIndicator.Builder()
                .setProgress(progress.fraction)
                .setCircularProgressIndicatorColors(ProgressIndicatorColors(ringColor, 0xFF2B2B2B.toInt()))
                .build()

            val openApp = ModifiersBuilders.Clickable.Builder()
                .setId(OPEN_APP_ID)
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(MainActivity::class.java.name)
                                .build(),
                        )
                        .build(),
                )
                .build()

            val total = LayoutElementBuilders.Box.Builder()
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
                .addContent(
                    Text.Builder(context, context.getString(R.string.tile_ml, progress.totalMl.withThousands()))
                        .setTypography(Typography.TYPOGRAPHY_DISPLAY3)
                        .setColor(WHITE)
                        .build(),
                )
                .build()

            // LoadAction: el sistema vuelve a pedir la Tile con el id del botón tocado
            val addClick = ModifiersBuilders.Clickable.Builder()
                .setId(ADD_PREFIX + System.currentTimeMillis())
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build()
            val addChip = CompactChip.Builder(context, context.getString(R.string.tile_add, QUICK_ML), addClick, device)
                .setChipColors(ChipColors(0xFF0288D1.toInt(), 0xFFFFFFFF.toInt()))
                .build()

            val content = LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(total)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                .addContent(addChip)
                .build()

            val primaryLabel = Text.Builder(context, context.getString(R.string.tile_today))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .setColor(LIGHT_WATER)
                .build()

            val secondaryText = if (progress.goalMet) {
                context.getString(R.string.tile_goal_met)
            } else {
                context.getString(R.string.tile_of_goal, progress.goalMl.withThousands())
            }
            val secondaryLabel = Text.Builder(context, secondaryText)
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setColor(if (progress.goalMet) GREEN else LIGHT_GRAY)
                .build()

            return EdgeContentLayout.Builder(device)
                .setEdgeContent(ring)
                .setPrimaryLabelTextContent(primaryLabel)
                .setContent(content)
                .setSecondaryLabelTextContent(secondaryLabel)
                .build()
        }
    }
}
