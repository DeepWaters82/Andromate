package com.example.modules.caffeine

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.core.preferences.ModuYouPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CaffeineTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val config = ModuYouPreferences.getInstance(this@CaffeineTileService)
                .caffeineConfigFlow
                .first()
            applyTileState(config.isActive)
        }
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val prefs = ModuYouPreferences.getInstance(this@CaffeineTileService)
            val current = prefs.caffeineConfigFlow.first()
            val nextActive = !current.isActive
            applyTileState(nextActive)
            if (nextActive) {
                CaffeineController.activate(this@CaffeineTileService)
            } else {
                CaffeineController.deactivate(this@CaffeineTileService)
            }
        }
    }

    private fun applyTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.caffeine_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_caffeine)
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Écran actif" else "Éteint"
        }
        tile.contentDescription = if (isActive) {
            "Caféine activée, appuyer pour autoriser la mise en veille"
        } else {
            "Caféine désactivée, appuyer pour garder l'écran allumé"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
