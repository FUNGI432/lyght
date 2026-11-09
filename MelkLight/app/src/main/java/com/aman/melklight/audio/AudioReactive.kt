package com.aman.melklight.audio

import android.graphics.Color

/**
 * Mirrors LYGHT for PC's audio.rs: software patterns sharing the light's own
 * on-device EQ-mode names (Energic/Rhythm/Spectrum/Rolling x2), driven by
 * audio we capture ourselves - the device's onboard mic has no external
 * read-back path at all (see PROTOCOL.md / the decompiled StreamingFragment.java),
 * so these are our own implementations, not the device's internal algorithms.
 */
enum class AudioPattern(val isSlow: Boolean) {
    ENERGIC(false),
    RHYTHM(false),
    SPECTRUM(false),
    ROLLING(false),
    ENERGIC2(true),
    RHYTHM2(true),
    SPECTRUM2(true),
    ROLLING2(true);

    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { ROLLING2 }
    }
}

/** Ticked once per frame with the live mic level (0..1) and the current base
 * color; returns the next RGB frame to send. The "2" variants react to a
 * smoothed signal instead of the raw one, same as the PC app. */
class AudioReactiveEngine {
    var baseColor: Triple<Int, Int, Int> = Triple(255, 255, 255)
    private var huePhase = 0f
    private var smoothLevel = 0f

    fun tick(dtSeconds: Float, rawLevel: Float, pattern: AudioPattern): Triple<Int, Int, Int> {
        smoothLevel += (rawLevel - smoothLevel) * 0.15f
        val level = if (pattern.isSlow) smoothLevel else rawLevel

        val (br, bg, bb) = baseColor
        val hsv = FloatArray(3)
        Color.RGBToHSV(br, bg, bb, hsv)
        val h0 = hsv[0]
        val s0 = hsv[1]

        return when (pattern) {
            AudioPattern.ENERGIC, AudioPattern.ENERGIC2 -> Triple(
                (br * level).toInt().coerceIn(0, 255),
                (bg * level).toInt().coerceIn(0, 255),
                (bb * level).toInt().coerceIn(0, 255)
            )
            AudioPattern.RHYTHM, AudioPattern.RHYTHM2 ->
                if (level > 0.5f) Triple(br, bg, bb) else Triple(0, 0, 0)
            AudioPattern.SPECTRUM, AudioPattern.SPECTRUM2 -> {
                huePhase = (huePhase + level * 180f * dtSeconds).mod(360f)
                hsvToRgb(h0 + huePhase, s0.coerceAtLeast(0.6f), level.coerceAtLeast(0.15f))
            }
            AudioPattern.ROLLING, AudioPattern.ROLLING2 -> {
                huePhase = (huePhase + 60f * dtSeconds).mod(360f)
                hsvToRgb(huePhase, s0.coerceAtLeast(0.6f), level.coerceAtLeast(0.15f))
            }
        }
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
        val color = Color.HSVToColor(floatArrayOf(h.mod(360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return Triple(Color.red(color), Color.green(color), Color.blue(color))
    }
}
