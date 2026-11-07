package com.aman.melklight.ble

import android.content.Context
import android.content.Intent
import android.os.Build
import com.aman.melklight.Prefs

/** Shared helper so MainActivity, the widget and the QS tile all send commands the same way. */
object Commander {
    fun send(context: Context, command: ByteArray, address: String? = null) {
        val target = address ?: Prefs.getDeviceAddress(context) ?: return
        val intent = Intent(context, BleCommandService::class.java).apply {
            action = BleCommandService.ACTION_SEND
            putExtra(BleCommandService.EXTRA_ADDRESS, target)
            putExtra(BleCommandService.EXTRA_COMMAND, command)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Connects without queuing a command - used to verify a newly-picked device is reachable. */
    fun connectOnly(context: Context, address: String) {
        val intent = Intent(context, BleCommandService::class.java).apply {
            action = BleCommandService.ACTION_CONNECT
            putExtra(BleCommandService.EXTRA_ADDRESS, address)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
