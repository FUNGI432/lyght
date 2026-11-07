package com.aman.melklight.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aman.melklight.Prefs
import com.aman.melklight.ble.Commander
import com.aman.melklight.protocol.Protocol

class LedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val address = Prefs.getDeviceAddress(this)
        if (address == null) {
            refresh()
            return
        }
        val newState = !Prefs.getWidgetPower(this)
        Prefs.setWidgetPower(this, newState)
        Commander.send(this, Protocol.power(newState), address)
        refresh()
        com.aman.melklight.widget.LedWidgetProvider.requestUpdate(this)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val address = Prefs.getDeviceAddress(this)
        if (address == null) {
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            tile.state = if (Prefs.getWidgetPower(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
