package com.aman.melklight.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.aman.melklight.R
import com.aman.melklight.ble.Commander
import com.aman.melklight.protocol.EffectCatalog
import com.aman.melklight.protocol.Protocol
import java.util.Calendar

class MoreFragment : Fragment(R.layout.fragment_more) {

    private val weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val micEqLabels = listOf("Energic", "Rhythm", "Spectrum", "Rolling", "Energic 2", "Rhythm 2", "Spectrum 2", "Rolling 2")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        view.findViewById<SwitchMaterial>(R.id.micSwitch).setOnCheckedChangeListener { _, isChecked ->
            Commander.send(context, Protocol.micOnOff(isChecked))
        }
        view.findViewById<SeekBar>(R.id.micSensitivitySeek).setOnSeekBarChangeListener(stopOnlyListener {
            Commander.send(context, Protocol.micSensitivity(it))
        })

        val micEqGroup = view.findViewById<ChipGroup>(R.id.micEqChipGroup)
        micEqLabels.forEachIndexed { index, label ->
            val chip = Chip(context).apply { text = label; isCheckable = true }
            chip.setOnClickListener { Commander.send(context, Protocol.micEqMode(index)) }
            micEqGroup.addView(chip)
        }

        val pinGroup = view.findViewById<ChipGroup>(R.id.pinChipGroup)
        EffectCatalog.pinSequences.forEach { option ->
            val chip = Chip(context).apply { text = option.label; isCheckable = true }
            chip.setOnClickListener {
                Commander.send(context, Protocol.pinSequence(option.rgb.first, option.rgb.second, option.rgb.third))
            }
            pinGroup.addView(chip)
        }

        view.findViewById<android.widget.Button>(R.id.syncTimeButton).setOnClickListener {
            val now = Calendar.getInstance()
            val weekday = now.get(Calendar.DAY_OF_WEEK) - 1
            Commander.send(
                context,
                Protocol.systemTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND), weekday)
            )
        }

        val weekdayGroup = view.findViewById<ChipGroup>(R.id.weekdayChipGroup)
        val weekdayChips = weekdayLabels.map { label ->
            Chip(context).apply { text = label; isCheckable = true }.also { weekdayGroup.addView(it) }
        }

        val timePicker = view.findViewById<TimePicker>(R.id.scheduleTimePicker)
        view.findViewById<android.widget.Button>(R.id.setScheduleButton).setOnClickListener {
            val hour: Int
            val minute: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = timePicker.hour
                minute = timePicker.minute
            } else {
                @Suppress("DEPRECATION")
                hour = timePicker.currentHour
                @Suppress("DEPRECATION")
                minute = timePicker.currentMinute
            }
            var weekdayBitmask = 0
            weekdayChips.forEachIndexed { index, chip -> if (chip.isChecked) weekdayBitmask = weekdayBitmask or (1 shl index) }
            // shortcut: timingMode fixed to 1 (enable) - the decompiled app never exercises
            // enough of this path to pin down the byte's exact meaning.
            Commander.send(context, Protocol.timingSchedule(hour, minute, 0, 1, weekdayBitmask))
        }
    }

    private fun stopOnlyListener(onStop: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            onStop(seekBar?.progress ?: 0)
        }
    }
}
