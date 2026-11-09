package com.aman.melklight.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.aman.melklight.R
import com.aman.melklight.audio.AudioPattern
import com.aman.melklight.audio.AudioReactiveEngine
import com.aman.melklight.audio.MicCapture
import com.aman.melklight.ble.Commander
import com.aman.melklight.ble.LedState
import com.aman.melklight.protocol.EffectCatalog
import com.aman.melklight.protocol.Protocol
import java.util.Calendar

class MoreFragment : Fragment(R.layout.fragment_more) {

    private val weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val micEqLabels = listOf("Energic", "Rhythm", "Spectrum", "Rolling", "Energic 2", "Rhythm 2", "Spectrum 2", "Rolling 2")

    private val micCapture = MicCapture()
    private val audioEngine = AudioReactiveEngine()
    private val tickHandler = Handler(Looper.getMainLooper())
    private var audioPattern = AudioPattern.ENERGIC
    private var audioGain = 3f
    private var driving = false
    private var lastTick = 0L
    private var audioMeter: ProgressBar? = null

    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else view?.findViewById<SwitchMaterial>(R.id.audioListenSwitch)?.isChecked = false
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = if (lastTick == 0L) 0.08f else ((now - lastTick) / 1000f).coerceIn(0.01f, 0.3f)
            lastTick = now
            val level = (micCapture.level * audioGain).coerceIn(0f, 1f)
            audioMeter?.progress = (level * 100).toInt()
            val ctx = context
            if (driving && ctx != null) {
                audioEngine.baseColor = LedState.baseColor
                val (r, g, b) = audioEngine.tick(dt, level, audioPattern)
                Commander.send(ctx, Protocol.musicReactiveColor(r, g, b))
            }
            tickHandler.postDelayed(this, 80L)
        }
    }

    private fun startListening() {
        micCapture.start()
        lastTick = 0L
        tickHandler.removeCallbacks(tickRunnable)
        tickHandler.post(tickRunnable)
    }

    private fun stopListening() {
        tickHandler.removeCallbacks(tickRunnable)
        micCapture.stop()
        driving = false
        audioMeter?.progress = 0
    }

    override fun onPause() {
        super.onPause()
        view?.findViewById<SwitchMaterial>(R.id.audioListenSwitch)?.isChecked = false
        view?.findViewById<SwitchMaterial>(R.id.audioDriveSwitch)?.isChecked = false
        stopListening()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        audioMeter = view.findViewById(R.id.audioMeter)
        val listenSwitch = view.findViewById<SwitchMaterial>(R.id.audioListenSwitch)
        val driveSwitch = view.findViewById<SwitchMaterial>(R.id.audioDriveSwitch)
        val gainSeek = view.findViewById<SeekBar>(R.id.audioGainSeek)
        val audioPatternGroup = view.findViewById<ChipGroup>(R.id.audioPatternChipGroup)

        micEqLabels.forEachIndexed { index, label ->
            val chip = Chip(context).apply { text = label; isCheckable = true; isChecked = index == 0 }
            chip.setOnClickListener { audioPattern = AudioPattern.fromIndex(index) }
            audioPatternGroup.addView(chip)
        }

        gainSeek.setOnSeekBarChangeListener(stopOnlyListener { audioGain = (it / 10f).coerceAtLeast(0.1f) })

        listenSwitch.setOnCheckedChangeListener { _, isChecked ->
            driveSwitch.isEnabled = isChecked
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                driveSwitch.isChecked = false
                stopListening()
            }
        }
        driveSwitch.setOnCheckedChangeListener { _, isChecked -> driving = isChecked }

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
