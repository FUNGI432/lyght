package com.aman.melklight.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.aman.melklight.R
import com.aman.melklight.protocol.Protocol
import java.util.ArrayDeque

/**
 * Minimal foreground service: connect to the saved MELK device, flush queued command
 * packets to it, then disconnect after a short idle window. Intentionally not a
 * persistent connection - keeps the app "lightweight" at the cost of ~1s reconnect
 * latency on the first command after being idle.
 */
class BleCommandService : Service() {

    companion object {
        const val ACTION_SEND = "com.aman.melklight.action.SEND"
        const val ACTION_CONNECT = "com.aman.melklight.action.CONNECT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_COMMAND = "command"
        private const val CHANNEL_ID = "ble_command_channel"
        private const val NOTIF_ID = 1
        private const val IDLE_DISCONNECT_MS = 5000L
        private const val WRITE_SPACING_MS = 20L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var connectedAddress: String? = null
    private val pending = ArrayDeque<ByteArray>()
    private var writing = false

    private val idleDisconnectRunnable = Runnable { teardown() }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                LedState.update(ConnectionState.CONNECTING)
                try {
                    g.discoverServices()
                } catch (_: SecurityException) {
                    teardown()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                LedState.update(ConnectionState.DISCONNECTED)
                g.close()
                if (gatt == g) {
                    gatt = null
                    connectedAddress = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            LedState.update(ConnectionState.CONNECTED)
            flushQueue()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writing = false
            handler.postDelayed({ flushQueue() }, WRITE_SPACING_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LedState.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.ble_channel_name), NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.ble_toast_title))
            .setSmallIcon(R.drawable.ic_power)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIF_ID, notification)

        when (intent?.action) {
            ACTION_SEND -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val command = intent.getByteArrayExtra(EXTRA_COMMAND)
                if (address != null && command != null) {
                    handler.removeCallbacks(idleDisconnectRunnable)
                    pending.add(command)
                    ensureConnected(address)
                }
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (address != null) {
                    handler.removeCallbacks(idleDisconnectRunnable)
                    ensureConnected(address)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureConnected(address: String) {
        if (gatt != null && connectedAddress == address) {
            flushQueue()
            return
        }
        teardownGattOnly()
        connectedAddress = address
        LedState.update(ConnectionState.CONNECTING)
        try {
            val adapter = (getSystemService(BluetoothManager::class.java)).adapter
            val device = adapter.getRemoteDevice(address)
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            LedState.update(ConnectionState.DISCONNECTED)
            stopSelf()
        } catch (_: IllegalArgumentException) {
            LedState.update(ConnectionState.DISCONNECTED)
            stopSelf()
        }
    }

    private fun flushQueue() {
        val g = gatt ?: return
        if (writing) return
        val service = g.getService(Protocol.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(Protocol.CHARACTERISTIC_UUID) ?: return
        val next = pending.poll()
        if (next == null) {
            handler.postDelayed(idleDisconnectRunnable, IDLE_DISCONNECT_MS)
            return
        }
        writing = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, next, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                characteristic.value = next
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        } catch (_: SecurityException) {
            writing = false
            teardown()
        }
    }

    private fun teardownGattOnly() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        connectedAddress = null
        pending.clear()
        writing = false
    }

    private fun teardown() {
        teardownGattOnly()
        LedState.update(ConnectionState.DISCONNECTED)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        teardownGattOnly()
        super.onDestroy()
    }
}
