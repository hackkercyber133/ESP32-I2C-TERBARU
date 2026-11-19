package __PKG__

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

abstract class NexusTileBase : TileService() {
    protected abstract fun render(tile: Tile, s: Snap)
    protected abstract fun nextParams(s: Snap): Map<String, Any>

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    protected fun refreshTile() {
        val tile = qsTile ?: return
        render(tile, NexusSnapshot.load(this))
        tile.updateTile()
    }

    protected fun setSubtitle(tile: Tile, text: String) {
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text
    }

    override fun onClick() {
        super.onClick()
        val s = NexusSnapshot.load(this)
        if (!s.configured) {
            openApp()
            return
        }
        val params = nextParams(s)
        val tile = qsTile
        if (tile != null) {
            setSubtitle(tile, "Mengirim...")
            tile.updateTile()
        }
        val appCtx = applicationContext
        Thread {
            val r = NexusControl.send(appCtx, params)
            Handler(Looper.getMainLooper()).post {
                refreshTile()
                if (!r.first) Toast.makeText(appCtx, r.second, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }
}

class VoltageTileService : NexusTileBase() {
    override fun render(tile: Tile, s: Snap) {
        tile.label = "Voltase"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nexus_bolt)
        if (!s.configured) {
            tile.state = Tile.STATE_INACTIVE
            setSubtitle(tile, "Buka app dulu")
        } else {
            tile.state = Tile.STATE_ACTIVE
            setSubtitle(tile, s.voltage.toInt().toString() + "V")
        }
    }

    override fun nextParams(s: Snap): Map<String, Any> {
        val steps = listOf(5, 9, 12)
        val cur = s.voltage.toInt()
        val next = steps.firstOrNull { it > cur } ?: steps.first()
        return mapOf("voltage" to next.toDouble())
    }
}

class FanTileService : NexusTileBase() {
    override fun render(tile: Tile, s: Snap) {
        tile.label = "Kipas"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nexus_fan)
        if (!s.configured) {
            tile.state = Tile.STATE_INACTIVE
            setSubtitle(tile, "Buka app dulu")
        } else {
            tile.state = if (s.fan > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitle(tile, s.fan.toString() + "%")
        }
    }

    override fun nextParams(s: Snap): Map<String, Any> {
        return mapOf("fanSpeed" to NexusWidgetProvider.nextFan(s.fan))
    }
}

class LedTileService : NexusTileBase() {
    override fun render(tile: Tile, s: Snap) {
        tile.label = "LED RGB"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nexus_bulb)
        if (!s.configured) {
            tile.state = Tile.STATE_INACTIVE
            setSubtitle(tile, "Buka app dulu")
        } else {
            val on = s.ledMode != "off"
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            setSubtitle(tile, if (on) "ON" else "OFF")
        }
    }

    override fun nextParams(s: Snap): Map<String, Any> {
        return mapOf("ledMode" to (if (s.ledMode != "off") "off" else s.lastLed))
    }
}
