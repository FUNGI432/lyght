package com.aman.melklight.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aman.melklight.widget.LedWidgetProvider

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/** In-process pub/sub so MainActivity (and the widget) can reflect what BleCommandService is doing. */
object LedState {
    @Volatile
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    /** The hero color picker's current RGB, kept live so audio-reactive mode
     * follows it instead of freezing at whatever it was when enabled. */
    @Volatile
    var baseColor: Triple<Int, Int, Int> = Triple(255, 255, 255)
        private set

    fun updateBaseColor(r: Int, g: Int, b: Int) {
        baseColor = Triple(r, g, b)
    }

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(ConnectionState) -> Unit>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun addListener(listener: (ConnectionState) -> Unit) {
        listeners.add(listener)
        listener(connectionState)
    }

    fun removeListener(listener: (ConnectionState) -> Unit) {
        listeners.remove(listener)
    }

    fun update(state: ConnectionState) {
        connectionState = state
        mainHandler.post {
            listeners.toList().forEach { it(state) }
        }
        appContext?.let { LedWidgetProvider.requestUpdate(it) }
    }
}
