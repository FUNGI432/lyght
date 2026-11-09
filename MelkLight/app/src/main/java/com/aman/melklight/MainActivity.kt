package com.aman.melklight

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayoutMediator
import com.aman.melklight.ble.BleScanner
import com.aman.melklight.ble.Commander
import com.aman.melklight.ble.ConnectionState
import com.aman.melklight.ble.FoundDevice
import com.aman.melklight.ble.LedState
import com.aman.melklight.databinding.ActivityMainBinding
import com.aman.melklight.protocol.Protocol
import com.aman.melklight.ui.MainPagerAdapter
import com.aman.melklight.widget.LedWidgetProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: BleScanner

    private var hue = 30f
    private var saturation = 1f
    private var inWhiteMode = false
    private var suppressPowerCallback = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) startScan()
    }
    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { startScan() }

    private val stateListener: (ConnectionState) -> Unit = { state -> runOnUiThread { renderStatus(state) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        scanner = BleScanner(this)

        binding.viewPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_effects) else getString(R.string.tab_more)
        }.attach()

        setupHero()
        renderDeviceChips()
        renderStatus(LedState.connectionState)
    }

    override fun onStart() {
        super.onStart()
        LedState.addListener(stateListener)
    }

    override fun onStop() {
        super.onStop()
        LedState.removeListener(stateListener)
        scanner.stop()
    }

    private fun renderStatus(state: ConnectionState) {
        val name = Prefs.getActiveDevice(this)?.name
        binding.statusText.text = when {
            name == null -> getString(R.string.status_disconnected)
            state == ConnectionState.CONNECTED -> getString(R.string.status_connected, name)
            state == ConnectionState.CONNECTING -> getString(R.string.status_connecting)
            else -> getString(R.string.status_connected, name) + " · idle"
        }
    }

    // ---------- Device switcher ----------

    private fun renderDeviceChips() {
        binding.deviceChipGroup.removeAllViews()
        val devices = Prefs.getDevices(this)
        val active = Prefs.getActiveAddress(this)

        devices.forEach { device ->
            val chip = Chip(this).apply {
                text = device.name
                isCheckable = true
                isChecked = device.address == active
            }
            chip.setOnClickListener {
                Prefs.setActiveAddress(this, device.address)
                renderStatus(ConnectionState.CONNECTING)
                Commander.connectOnly(this, device.address)
                LedWidgetProvider.requestUpdate(this)
            }
            chip.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.action_remove_device) + " " + device.name)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Prefs.removeDevice(this, device.address)
                        renderDeviceChips()
                        renderStatus(LedState.connectionState)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            binding.deviceChipGroup.addView(chip)
        }

        val addChip = Chip(this).apply { text = getString(R.string.action_add_device) }
        addChip.setOnClickListener { requestPermissionsThenScan() }
        binding.deviceChipGroup.addView(addChip)
    }

    private fun requestPermissionsThenScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += android.Manifest.permission.BLUETOOTH_SCAN
            needed += android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            needed += android.Manifest.permission.ACCESS_FINE_LOCATION
        }
        val missing = needed.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else startScan()
    }

    private fun startScan() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return
        if (!adapter.isEnabled) {
            enableBtLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        val found = linkedMapOf<String, FoundDevice>()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scanning_title)
            .setNegativeButton(android.R.string.cancel) { d, _ -> scanner.stop(); d.dismiss() }
            .create()
        val listAdapter = android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val listView = android.widget.ListView(this)
        listView.adapter = listAdapter
        dialog.setView(listView)
        listView.setOnItemClickListener { _, _, position, _ ->
            val key = listAdapter.getItem(position) ?: return@setOnItemClickListener
            val device = found[key] ?: return@setOnItemClickListener
            Prefs.addDevice(this, device.address, device.name)
            renderDeviceChips()
            renderStatus(ConnectionState.CONNECTING)
            Commander.connectOnly(this, device.address)
            LedWidgetProvider.requestUpdate(this)
            scanner.stop()
            dialog.dismiss()
        }
        dialog.show()
        scanner.start(
            onFound = { device ->
                val label = "${device.name}  (${device.address})"
                if (!found.containsKey(label)) {
                    found[label] = device
                    runOnUiThread { listAdapter.add(label) }
                }
            },
            onStopped = {}
        )
    }

    // ---------- Hero: power / color / brightness ----------

    private fun setupHero() {
        binding.powerButton.setOnClickListener {
            val newState = !Prefs.getWidgetPower(this)
            Prefs.setWidgetPower(this, newState)
            binding.powerButton.alpha = if (newState) 1f else 0.4f
            Commander.send(this, Protocol.power(newState))
            LedWidgetProvider.requestUpdate(this)
        }
        binding.powerButton.alpha = if (Prefs.getWidgetPower(this)) 1f else 0.4f

        binding.colorModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            inWhiteMode = checkedId == binding.whiteModeButton.id
            binding.colorSliders.visibility = if (inWhiteMode) View.GONE else View.VISIBLE
            binding.whiteSliders.visibility = if (inWhiteMode) View.VISIBLE else View.GONE
            sendBrightness()
        }
        binding.colorModeButton.isChecked = true

        setupGradientSliders()

        binding.warmSeek.setOnSeekBarChangeListener(stopListener { sendWhiteTemp() })
        binding.coldSeek.setOnSeekBarChangeListener(stopListener { sendWhiteTemp() })

        binding.brightnessSeek.progress = Prefs.getWidgetBrightness(this)
        updateBrightnessLabel()
        binding.brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateBrightnessLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Prefs.setWidgetBrightness(this@MainActivity, binding.brightnessSeek.progress)
                sendBrightness()
                LedWidgetProvider.requestUpdate(this@MainActivity)
            }
        })

        setupSwatches()
        updateColorPreview()
        val initial = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        LedState.updateBaseColor(Color.red(initial), Color.green(initial), Color.blue(initial))
    }

    private fun setupSwatches() {
        binding.swatchRow.removeAllViews()
        val sizePx = (36 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        Prefs.getSwatches(this).forEach { color ->
            val view = View(this)
            val params = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
            params.marginEnd = marginPx
            view.layoutParams = params
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
            bg.setColor(color or (0xFF shl 24))
            view.background = bg
            view.setOnClickListener {
                val hsv = FloatArray(3)
                Color.colorToHSV(color or (0xFF shl 24), hsv)
                hue = hsv[0]
                saturation = hsv[1]
                binding.hueSeek.progress = hue.toInt()
                binding.saturationSeek.progress = (saturation * 100).toInt()
                updateSaturationGradient()
                updateColorPreview()
                sendColor()
            }
            binding.swatchRow.addView(view)
        }
    }

    private fun updateBrightnessLabel() {
        binding.brightnessValue.text = "${binding.brightnessSeek.progress}%"
    }

    private fun sendBrightness() {
        val lightMode = if (inWhiteMode) 3 else 1
        Commander.send(this, Protocol.brightness(binding.brightnessSeek.progress, lightMode))
    }

    private fun setupGradientSliders() {
        val hueColors = IntArray(13) { i -> Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)) }
        binding.hueSeek.progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hueColors).apply {
            cornerRadius = 18f * resources.displayMetrics.density
        }
        binding.hueSeek.splitTrack = false
        binding.hueSeek.progress = hue.toInt()

        binding.saturationSeek.splitTrack = false
        binding.saturationSeek.progress = (saturation * 100).toInt()
        updateSaturationGradient()

        binding.hueSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                hue = progress.toFloat()
                updateSaturationGradient()
                updateColorPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendColor()
            }
        })
        binding.saturationSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saturation = progress / 100f
                updateColorPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendColor()
            }
        })
    }

    private fun updateSaturationGradient() {
        val fullHue = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        binding.saturationSeek.progressDrawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.WHITE, fullHue)
        ).apply { cornerRadius = 18f * resources.displayMetrics.density }
    }

    private fun updateColorPreview() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        binding.colorPreview.setBackgroundColor(color)
    }

    private fun sendColor() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        LedState.updateBaseColor(Color.red(color), Color.green(color), Color.blue(color))
        Commander.send(this, Protocol.color(Color.red(color), Color.green(color), Color.blue(color)))
    }

    private fun sendWhiteTemp() {
        Commander.send(this, Protocol.whiteTemperature(binding.warmSeek.progress, binding.coldSeek.progress))
    }

    private fun stopListener(onStop: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) = onStop()
    }
}
