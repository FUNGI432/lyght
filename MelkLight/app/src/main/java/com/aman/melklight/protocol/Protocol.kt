package com.aman.melklight.protocol

import java.util.UUID

/**
 * MELK / ELK-BLEDOM BLE command protocol, reverse-engineered from the decompiled
 * MagicLantern-V6.11.10.apk (BluetoothLEService.java / BluetoothUtil.java).
 * Commands are plain 9-byte packets written to [CHARACTERISTIC_UUID] - no encryption
 * (the app ships an AES/XOR class but never calls it).
 */
object Protocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    const val DEVICE_NAME_PREFIX = "MELK-"

    private fun b(v: Int) = v.toByte()
    private const val HEAD = 0x7E
    private const val TAIL = 0xEF

    fun power(on: Boolean): ByteArray {
        val v = if (on) 1 else 0
        return byteArrayOf(b(HEAD), b(0x04), b(0x04), b(v), b(0x00), b(v), b(0xFF), b(0x00), b(TAIL))
    }

    fun color(r: Int, g: Int, b2: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x07), b(0x05), b(0x03), b(r), b(g), b(b2), b(0x10), b(TAIL))

    fun musicReactiveColor(r: Int, g: Int, b2: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x07), b(0x05), b(0x03), b(r), b(g), b(b2), b(0x20), b(TAIL))

    fun whiteTemperature(warm: Int, cold: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x06), b(0x05), b(0x02), b(warm), b(cold), b(0xFF), b(0x08), b(TAIL))

    /** lightMode: 0=ALL, 1=RGB, 2=W, 3=CT (matches the original LightMode constants). */
    fun brightness(level: Int, lightMode: Int = 0xFF): ByteArray =
        byteArrayOf(b(HEAD), b(0x04), b(0x01), b(level), b(lightMode), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    /** code comes from [EffectCatalog] (modes_group0..7). */
    fun effectMode(code: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x05), b(0x03), b(code), b(0x06), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    fun effectSpeed(speed: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x04), b(0x02), b(speed), b(0xFF), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    /** index 1-28, from [EffectCatalog] "Scenes" group. */
    fun scene(index: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x05), b(0x31), b(index), b(0x07), b(0xFF), b(0xFF), b(0x01), b(TAIL))

    fun singleColorPreset(index: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x05), b(0x05), b(0x01), b(index), b(0xFF), b(0xFF), b(0x08), b(TAIL))

    /** lightMode: 0=ALL,1=RGB,2=W,3=CT. Direct port of BluetoothLEService.changeRGBWStatus. */
    fun rgbwChannels(rgbOn: Boolean, wOn: Boolean, cctOn: Boolean, lightMode: Int): ByteArray {
        var flags = if (rgbOn) 1 shl 16 else 0
        if (wOn) flags = flags or 1
        if (cctOn) flags = flags or (1 shl 8)
        var i = if (((flags shr 16) and 1) == 1) 1 else 0
        val i2 = if ((flags and 1) == 1) 1 else 0
        val i3 = if (((flags shr 8) and 1) == 1) 1 else 0
        var flagsByte = if (i != 0) 0xE0 else 0
        if (i2 != 0) flagsByte = flagsByte or 0x10
        if (lightMode != 0 && lightMode != 1) {
            i = if (lightMode != 2) (if (lightMode != 3) 0 else i3) else i2
        }
        return byteArrayOf(b(HEAD), b(0x04), b(0x04), b(flagsByte), b(lightMode), b(i), b(0xFF), b(0x00), b(TAIL))
    }

    /** rgb = pin positions 0-2 each, e.g. from [EffectCatalog.pinSequences]. */
    fun pinSequence(r: Int, g: Int, b2: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x06), b(0x81), b(r), b(g), b(b2), b(0xFF), b(0x00), b(TAIL))

    fun micOnOff(on: Boolean): ByteArray =
        byteArrayOf(b(HEAD), b(0x04), b(0x07), b(if (on) 1 else 0), b(0xFF), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    fun micSensitivity(level: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x04), b(0x06), b(level), b(0xFF), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    /** mode 0-7, see ExternalMicEqMode (Energic/Rhythm/Spectrum/Rolling, x2 variants). */
    fun micEqMode(mode: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x07), b(0x03), b(mode + 128), b(0x04), b(0xFF), b(0xFF), b(0x00), b(TAIL))

    fun symphonyPoint(point: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x07), b(0x21), b(point and 0xFF), b((point shr 8) and 0xFF), b(0x00), b(0xFF), b(0x00), b(TAIL))

    fun systemTime(hour: Int, minute: Int, second: Int, weekday: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x07), b(0x83), b(hour), b(minute), b(second), b(weekday), b(0xFF), b(TAIL))

    /** timingMode: device-defined enable/repeat flag; weekdayBitmask: bit0=Sun..bit6=Sat (device convention). */
    fun timingSchedule(hour: Int, minute: Int, second: Int, timingMode: Int, weekdayBitmask: Int): ByteArray =
        byteArrayOf(b(HEAD), b(0x08), b(0x82), b(hour), b(minute), b(second), b(timingMode), b(weekdayBitmask), b(TAIL))
}
