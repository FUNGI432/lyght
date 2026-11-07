package com.aman.melklight.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * Colors the R/G/B/Y/C/P color-code letters (and full color words) inside effect
 * names like "R-G-B Jump" or "Red Marquee" so the name visually hints at the effect.
 */
object EffectNameColorizer {
    private val codeColors = mapOf(
        "R" to Color.parseColor("#FF5252"),
        "G" to Color.parseColor("#4CAF50"),
        "B" to Color.parseColor("#448AFF"),
        "Y" to Color.parseColor("#FFD740"),
        "C" to Color.parseColor("#26C6DA"),
        "P" to Color.parseColor("#E040FB"),
    )
    private val wordColors = mapOf(
        "Red" to codeColors.getValue("R"),
        "Green" to codeColors.getValue("G"),
        "Blue" to codeColors.getValue("B"),
        "Yellow" to codeColors.getValue("Y"),
        "Cyan" to codeColors.getValue("C"),
        "Purple" to codeColors.getValue("P"),
    )

    fun colorize(name: String): SpannableString {
        val spannable = SpannableString(name)
        var searchFrom = 0
        for (word in name.split(" ")) {
            val wordStart = name.indexOf(word, searchFrom)
            if (wordStart < 0) continue
            if (word.contains("-")) {
                var partStart = wordStart
                for (part in word.split("-")) {
                    codeColors[part]?.let { applySpan(spannable, partStart, partStart + part.length, it) }
                    partStart += part.length + 1
                }
            } else {
                (wordColors[word] ?: codeColors[word])?.let { applySpan(spannable, wordStart, wordStart + word.length, it) }
            }
            searchFrom = wordStart + word.length
        }
        return spannable
    }

    private fun applySpan(s: SpannableString, start: Int, end: Int, color: Int) {
        if (start < 0 || end > s.length || start >= end) return
        s.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
