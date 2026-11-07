package com.aman.melklight

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedDevice(val address: String, val name: String)

object Prefs {
    private const val FILE = "melklight_prefs"
    private const val KEY_DEVICES = "devices"
    private const val KEY_ACTIVE_ADDRESS = "active_address"
    private const val KEY_SWATCHES = "swatches"

    // legacy single-device keys, migrated into the device list on first read
    private const val KEY_LEGACY_ADDRESS = "device_address"
    private const val KEY_LEGACY_NAME = "device_name"

    // the 7 basic colors the original app's "7-Color" effects cycle through
    val defaultSwatches = listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0xFFFFFF)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun migrateLegacyIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_LEGACY_ADDRESS) && !p.contains(KEY_DEVICES)) {
            val address = p.getString(KEY_LEGACY_ADDRESS, null)
            val name = p.getString(KEY_LEGACY_NAME, null) ?: address
            if (address != null) {
                saveDevices(context, listOf(SavedDevice(address, name ?: address)))
                p.edit().putString(KEY_ACTIVE_ADDRESS, address).remove(KEY_LEGACY_ADDRESS).remove(KEY_LEGACY_NAME).apply()
            }
        }
    }

    fun getDevices(context: Context): List<SavedDevice> {
        migrateLegacyIfNeeded(context)
        val raw = prefs(context).getString(KEY_DEVICES, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            SavedDevice(obj.getString("address"), obj.getString("name"))
        }
    }

    private fun saveDevices(context: Context, devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.forEach { d ->
            array.put(JSONObject().put("address", d.address).put("name", d.name))
        }
        prefs(context).edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    /** Adds the device if new (or updates its name if already saved), and makes it active. */
    fun addDevice(context: Context, address: String, name: String?) {
        val existing = getDevices(context).filterNot { it.address == address }
        saveDevices(context, existing + SavedDevice(address, name ?: address))
        setActiveAddress(context, address)
    }

    fun removeDevice(context: Context, address: String) {
        val remaining = getDevices(context).filterNot { it.address == address }
        saveDevices(context, remaining)
        if (getActiveAddress(context) == address) {
            prefs(context).edit().putString(KEY_ACTIVE_ADDRESS, remaining.firstOrNull()?.address).apply()
        }
    }

    fun getActiveAddress(context: Context): String? {
        migrateLegacyIfNeeded(context)
        return prefs(context).getString(KEY_ACTIVE_ADDRESS, null)
    }

    fun getActiveDevice(context: Context): SavedDevice? {
        val address = getActiveAddress(context) ?: return null
        return getDevices(context).firstOrNull { it.address == address }
    }

    fun setActiveAddress(context: Context, address: String) {
        prefs(context).edit().putString(KEY_ACTIVE_ADDRESS, address).apply()
    }

    // kept for the widget/tile/service, which only ever talk to the active device
    fun getDeviceAddress(context: Context): String? = getActiveAddress(context)
    fun getDeviceName(context: Context): String? = getActiveDevice(context)?.name

    fun getSwatches(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY_SWATCHES, null) ?: return defaultSwatches
        return raw.split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { defaultSwatches }
    }

    fun setSwatches(context: Context, colors: List<Int>) {
        prefs(context).edit().putString(KEY_SWATCHES, colors.joinToString(",")).apply()
    }

    // Widget's best-guess of current device state (we don't read live status back from
    // the device for this), used so the widget UI doesn't look blank between taps.
    private const val KEY_WIDGET_POWER = "widget_power"
    private const val KEY_WIDGET_BRIGHTNESS = "widget_brightness"

    fun getWidgetPower(context: Context): Boolean = prefs(context).getBoolean(KEY_WIDGET_POWER, true)

    fun setWidgetPower(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIDGET_POWER, on).apply()
    }

    fun getWidgetBrightness(context: Context): Int = prefs(context).getInt(KEY_WIDGET_BRIGHTNESS, 80)

    fun setWidgetBrightness(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_WIDGET_BRIGHTNESS, value).apply()
    }
}
