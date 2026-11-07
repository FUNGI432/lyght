package com.aman.melklight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.aman.melklight.MainActivity
import com.aman.melklight.Prefs
import com.aman.melklight.R
import com.aman.melklight.ble.Commander
import com.aman.melklight.ble.ConnectionState
import com.aman.melklight.ble.LedState
import com.aman.melklight.protocol.Protocol
import kotlin.math.max
import kotlin.math.min

class LedWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE_POWER = "com.aman.melklight.widget.TOGGLE_POWER"
        private const val ACTION_BRIGHTNESS_UP = "com.aman.melklight.widget.BRIGHTNESS_UP"
        private const val ACTION_BRIGHTNESS_DOWN = "com.aman.melklight.widget.BRIGHTNESS_DOWN"
        private const val ACTION_SWATCH = "com.aman.melklight.widget.SWATCH"
        private const val EXTRA_SWATCH_INDEX = "swatch_index"
        private const val BRIGHTNESS_STEP = 20
        private const val BRIGHTNESS_MAX = 100

        private val SWATCH_IDS = intArrayOf(
            R.id.widgetSwatch0, R.id.widgetSwatch1, R.id.widgetSwatch2, R.id.widgetSwatch3,
            R.id.widgetSwatch4, R.id.widgetSwatch5, R.id.widgetSwatch6
        )

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LedWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                LedWidgetProvider().onUpdate(context, manager, ids)
            }
        }

        private fun actionPendingIntent(context: Context, action: String, extraIndex: Int? = null): PendingIntent {
            val intent = Intent(context, LedWidgetProvider::class.java).apply {
                this.action = action
                extraIndex?.let { putExtra(EXTRA_SWATCH_INDEX, it) }
                data = android.net.Uri.parse("melklight://widget/$action/$extraIndex")
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val device = Prefs.getActiveDevice(context)
        val swatches = Prefs.getSwatches(context)
        val brightness = Prefs.getWidgetBrightness(context)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_led)

            val openAppIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetDeviceName, openAppIntent)

            val dotColor = when {
                device == null -> Color.parseColor("#5A5A5E")
                LedState.connectionState == ConnectionState.CONNECTED -> Color.parseColor("#4CAF50")
                LedState.connectionState == ConnectionState.CONNECTING -> Color.parseColor("#FFB454")
                else -> Color.parseColor("#5A5A5E")
            }
            views.setInt(R.id.widgetStatusDot, "setColorFilter", dotColor)

            if (device == null) {
                views.setTextViewText(R.id.widgetDeviceName, context.getString(R.string.widget_no_device))
                views.setTextViewText(R.id.widgetBrightnessLabel, "--")
                views.setOnClickPendingIntent(R.id.widgetPowerButton, openAppIntent)
            } else {
                views.setTextViewText(R.id.widgetDeviceName, device.name)
                views.setTextViewText(R.id.widgetBrightnessLabel, "$brightness%")
                views.setOnClickPendingIntent(R.id.widgetPowerButton, actionPendingIntent(context, ACTION_TOGGLE_POWER))
                views.setOnClickPendingIntent(R.id.widgetBrightnessUp, actionPendingIntent(context, ACTION_BRIGHTNESS_UP))
                views.setOnClickPendingIntent(R.id.widgetBrightnessDown, actionPendingIntent(context, ACTION_BRIGHTNESS_DOWN))

                for ((index, viewId) in SWATCH_IDS.withIndex()) {
                    val color = swatches.getOrNull(index)
                    if (color != null) {
                        views.setInt(viewId, "setColorFilter", color or (0xFF shl 24))
                        views.setOnClickPendingIntent(viewId, actionPendingIntent(context, ACTION_SWATCH, index))
                    }
                }
            }

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val address = Prefs.getActiveAddress(context) ?: return
        when (intent.action) {
            ACTION_TOGGLE_POWER -> {
                val newState = !Prefs.getWidgetPower(context)
                Prefs.setWidgetPower(context, newState)
                Commander.send(context, Protocol.power(newState), address)
            }
            ACTION_BRIGHTNESS_UP -> {
                val value = min(BRIGHTNESS_MAX, Prefs.getWidgetBrightness(context) + BRIGHTNESS_STEP)
                Prefs.setWidgetBrightness(context, value)
                Commander.send(context, Protocol.brightness(value), address)
            }
            ACTION_BRIGHTNESS_DOWN -> {
                val value = max(0, Prefs.getWidgetBrightness(context) - BRIGHTNESS_STEP)
                Prefs.setWidgetBrightness(context, value)
                Commander.send(context, Protocol.brightness(value), address)
            }
            ACTION_SWATCH -> {
                val index = intent.getIntExtra(EXTRA_SWATCH_INDEX, -1)
                val color = Prefs.getSwatches(context).getOrNull(index) ?: return
                Commander.send(
                    context,
                    Protocol.color((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF),
                    address
                )
            }
            else -> return
        }
        requestUpdate(context)
    }
}
