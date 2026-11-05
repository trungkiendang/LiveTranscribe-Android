package com.kun.livetranslation.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kun.livetranslation.LiveCaptionApp
import com.kun.livetranslation.MainActivity
import com.kun.livetranslation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Quick Settings tile (status-bar shade): one tap starts or stops captions.
 *
 * Stop goes straight to the running service. Start opens [MainActivity] with
 * [MainActivity.EXTRA_START_CAPTIONING] so the normal permission and screen-capture
 * consent flow runs (a tile cannot show those dialogs itself).
 */
class CaptionTileService : TileService() {
    private var scope: CoroutineScope? = null

    private val running: Boolean
        get() = (application as LiveCaptionApp).container.runtimeStore.state.value.running

    override fun onStartListening() {
        render()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            (application as LiveCaptionApp).container.runtimeStore.state.map { it.running }.collect { render() }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        if (running) {
            runCatching {
                startService(Intent(this, CaptionForegroundService::class.java).apply {
                    action = CaptionForegroundService.ACTION_STOP
                })
            }
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_START_CAPTIONING, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_caption)
        tile.updateTile()
    }
}
