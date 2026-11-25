// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.app.Activity
import android.graphics.Typeface
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.controller.JoyConProbe
import dev.jodacame.dsucontroller.controller.JoyConSide
import dev.jodacame.dsucontroller.controller.PlayerLightPattern
import dev.jodacame.dsucontroller.controller.PlayerLights
import dev.jodacame.dsucontroller.controller.SampleRateMeter
import dev.jodacame.dsucontroller.controller.SensorReading
import java.util.Locale

/**
 * Shows, for every connected Joy-Con, what this phone's Android actually delivers: the sensors
 * it exposes, live motion values and their rate, button and stick events, battery and player
 * lights. It answers the question "can this phone bridge Joy-Con motion?".
 *
 * The probes keep listening until the activity is destroyed, also while it is in the background
 * or the screen is off, because measuring that is part of the diagnosis. A summary line per
 * Joy-Con goes to logcat once a second so the result can be read without the screen.
 */
class DiagnosticsActivity : Activity(), InputManager.InputDeviceListener {
    private class Entry(
        val side: JoyConSide,
        val device: InputDevice,
        val probe: JoyConProbe,
        val playerLights: PlayerLights,
    ) {
        var lastKey: String = "none yet"
        var stick: String = "none yet"
        var playerId: Int = 0
    }

    private val entries = LinkedHashMap<Int, Entry>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler
    private lateinit var inputManager: InputManager
    private lateinit var report: TextView
    private var ticks = 0

    private val refresh = object : Runnable {
        override fun run() {
            report.text = buildReport()
            if (++ticks % TICKS_PER_LOG == 0) logSummary()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorThread = HandlerThread("joycon-diagnostics").apply { start() }
        sensorHandler = Handler(sensorThread.looper)
        inputManager = getSystemService(InputManager::class.java)
        setContentView(buildContent())

        inputManager.registerInputDeviceListener(this, mainHandler)
        inputManager.inputDeviceIds.forEach(::onInputDeviceAdded)
        mainHandler.post(refresh)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refresh)
        inputManager.unregisterInputDeviceListener(this)
        entries.keys.toList().forEach(::onInputDeviceRemoved)
        sensorThread.quitSafely()
        super.onDestroy()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        if (entries.containsKey(deviceId)) return
        val device = InputDevice.getDevice(deviceId) ?: return
        val side = JoyConSide.fromIds(device.vendorId, device.productId) ?: return
        val probe = JoyConProbe(device).apply { start(sensorHandler) }
        entries[deviceId] = Entry(side, device, probe, PlayerLights(device))
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val entry = entries.remove(deviceId) ?: return
        entry.probe.stop()
        entry.playerLights.close()
    }

    override fun onInputDeviceChanged(deviceId: Int) = Unit

    /**
     * Joy-Con keys are consumed here so they do not act on the screen itself: B is Android's
     * Back and would close the activity in the middle of a test.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val entry = entries[event.deviceId] ?: return super.dispatchKeyEvent(event)
        val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
        entry.lastKey = "${KeyEvent.keyCodeToString(event.keyCode)} $action"
        if (event.repeatCount == 0) Log.i(LOG_TAG, "${entry.side.name} key ${entry.lastKey}")
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val entry = entries[event.deviceId] ?: return super.dispatchGenericMotionEvent(event)
        entry.stick = STICK_AXES.joinToString("  ") { (name, axis) ->
            "$name=${format2(event.getAxisValue(axis))}"
        }
        return true
    }

    private fun buildContent(): LinearLayout {
        val padding = dp(16)
        val title = TextView(this).apply {
            setText(R.string.diagnostics_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        val lightsButton = Button(this).apply {
            setText(R.string.diagnostics_cycle_lights)
            setOnClickListener { cyclePlayerLights() }
        }
        report = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(report, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            fitsSystemWindows = true
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(lightsButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
    }

    /** Advances every Joy-Con to the next player id (1 to 4) so the user can see the lights change. */
    private fun cyclePlayerLights() {
        for (entry in entries.values) {
            if (!entry.playerLights.available) continue
            entry.playerId = entry.playerId % PlayerLightPattern.MAX_PLAYER + 1
            entry.playerLights.show(entry.playerId)
        }
    }

    private fun buildReport(): String {
        if (entries.isEmpty()) return getString(R.string.diagnostics_no_joycon)
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        return entries.values.joinToString("\n\n") { entry ->
            val snapshot = entry.probe.snapshot()
            buildString {
                appendLine(entry.side.label)
                appendLine("  sensors exposed : ${snapshot.exposedSensorNames.ifEmpty { listOf("none") }}")
                appendLine("  gyroscope       : ${describe(snapshot.gyroscope, "rad/s", nowNanos)}")
                appendLine("  accelerometer   : ${describe(snapshot.accelerometer, "m/s2", nowNanos)}")
                appendLine("  last key        : ${entry.lastKey}")
                appendLine("  stick           : ${entry.stick}")
                appendLine("  battery         : ${describe(entry.device.batteryState)}")
                append("  player lights   : ")
                append(
                    when {
                        !entry.playerLights.available -> "not exposed by this phone"
                        entry.playerId == 0 -> "available, showing the system default"
                        else -> "available, showing P${entry.playerId}"
                    },
                )
            }
        }
    }

    private fun describe(reading: SensorReading, unit: String, nowNanos: Long): String {
        if (!reading.available) return "not exposed by this phone"
        if (reading.lastArrivalNanos == SampleRateMeter.NO_TIMESTAMP) return "exposed, no data yet"
        val ageMs = (nowNanos - reading.lastArrivalNanos) / NANOS_PER_MILLI
        return "${format1(reading.ratePerSecond)} Hz  " +
            "x=${format2(reading.x)} y=${format2(reading.y)} z=${format2(reading.z)} $unit  " +
            "(${reading.totalSamples} samples, last $ageMs ms ago, " +
            "sensor clock x${format1(reading.sensorClockRatio)})"
    }

    private fun describe(battery: BatteryState): String {
        if (!battery.isPresent) return "not reported"
        val percent = if (battery.capacity.isNaN()) "unknown level" else "${(battery.capacity * 100).toInt()}%"
        return "$percent, status ${battery.status}"
    }

    private fun logSummary() {
        for (entry in entries.values) {
            val snapshot = entry.probe.snapshot()
            Log.i(
                LOG_TAG,
                "${entry.side.name} gyroHz=${format1(snapshot.gyroscope.ratePerSecond)} " +
                    "gyroSamples=${snapshot.gyroscope.totalSamples} " +
                    "gyroClock=x${format1(snapshot.gyroscope.sensorClockRatio)} " +
                    "accelHz=${format1(snapshot.accelerometer.ratePerSecond)} " +
                    "accelSamples=${snapshot.accelerometer.totalSamples}",
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun format1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun format2(value: Float): String = String.format(Locale.ROOT, "%+.2f", value)

    private companion object {
        const val LOG_TAG = "JoyConDiagnostics"
        const val REFRESH_INTERVAL_MS = 250L
        const val TICKS_PER_LOG = 4
        const val NANOS_PER_MILLI = 1_000_000L
        val STICK_AXES = listOf(
            "X" to MotionEvent.AXIS_X,
            "Y" to MotionEvent.AXIS_Y,
            "Z" to MotionEvent.AXIS_Z,
            "RZ" to MotionEvent.AXIS_RZ,
            "RX" to MotionEvent.AXIS_RX,
            "RY" to MotionEvent.AXIS_RY,
            "HAT_X" to MotionEvent.AXIS_HAT_X,
            "HAT_Y" to MotionEvent.AXIS_HAT_Y,
        )
    }
}
