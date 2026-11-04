package co.edu.ucc.hidrasmart.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Pide al sistema que vuelva a dibujar la Tile y la complicación de la esfera,
 * por ejemplo después de registrar una toma o de cambiar la meta diaria.
 */
object WatchSurfaces {

    fun refresh(context: Context, includeTile: Boolean = true) {
        val app = context.applicationContext
        if (includeTile) {
            runCatching { TileService.getUpdater(app).requestUpdate(HydrationTileService::class.java) }
        }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, HydrationComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
