package com.aman.melklight.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aman.melklight.protocol.Protocol

data class FoundDevice(val name: String, val address: String)

class BleScanner(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var callback: ScanCallback? = null

    fun start(onFound: (FoundDevice) -> Unit, onStopped: () -> Unit, timeoutMs: Long = 10_000) {
        if (scanning) return
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                if (name.startsWith(Protocol.DEVICE_NAME_PREFIX)) {
                    onFound(FoundDevice(name, result.device.address))
                }
            }
        }
        callback = cb
        try {
            scanner.startScan(null, settings, cb)
            scanning = true
            handler.postDelayed({ stop(onStopped) }, timeoutMs)
        } catch (_: SecurityException) {
            scanning = false
        }
    }

    fun stop(onStopped: () -> Unit = {}) {
        if (!scanning) return
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val scanner = adapter?.bluetoothLeScanner
        try {
            callback?.let { scanner?.stopScan(it) }
        } catch (_: SecurityException) {
        }
        scanning = false
        handler.removeCallbacksAndMessages(null)
        onStopped()
    }
}
