// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.controller.JoyConSide
import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.service.BridgeService
import dev.jodacame.dsucontroller.service.BridgeState
import dev.jodacame.dsucontroller.settings.BridgeSettings

/**
 * The dashboard (docs/contracts/dashboard-ui.md): four slot blocks, the client approval bar and
 * the status bar. It renders the bridge's state and forwards Joy-Con keys and stick to it.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val connection = BridgeConnection(this) { onBridgeConnected() }
    private lateinit var settings: BridgeSettings
    private lateinit var slotViews: List<SlotBlockView>
    private lateinit var addressText: TextView
    private lateinit var stateText: TextView
    private lateinit var primaryButton: Button
    private lateinit var approvalBar: LinearLayout
    private lateinit var approvalText: TextView
    private var shownPending: String? = null
    private var lastTouchMillis = 0L
    private var dimmed = false
    private var screenKeptOn = false

    private val refresh = object : Runnable {
        override fun run() {
            connection.bridge?.let { render(it.state()) }
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BridgeSettings(this)
        setContentView(buildContent())
    }

    override fun onStart() {
        super.onStart()
        connection.bind()
        handler.post(refresh)
    }

    override fun onResume() {
        super.onResume()
        lastTouchMillis = SystemClock.uptimeMillis()
        setDimmed(false)
        connection.bridge?.refreshLights()
    }

    override fun onPause() {
        // The screen is kept on only while this screen is in front (dashboard contract, rule 7).
        setKeepScreenOn(false)
        setDimmed(false)
        super.onPause()
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        connection.unbind()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        connection.bridge?.onKeyEvent(event) == true || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        connection.bridge?.onMotionEvent(event) == true || super.dispatchGenericMotionEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastTouchMillis = SystemClock.uptimeMillis()
        if (dimmed) {
            setDimmed(false)
            // The touch that wakes the screen does nothing else.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun onBridgeConnected() {
        connection.bridge?.refreshLights()
    }

    private fun buildContent(): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val gap = dp(GAP_DP)
        slotViews = List(DsuProtocol.SLOT_COUNT) { slot ->
            SlotBlockView(this, column = slot % 2, row = slot / 2).apply {
                setOnClickListener { openDiagnosticsIfNoMotion(slot) }
            }
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            for (row in 0..1) {
                val line = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (column in 0..1) {
                        addView(
                            slotViews[row * 2 + column],
                            LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                                if (column == 0) marginEnd = gap / 2 else marginStart = gap / 2
                            },
                        )
                    }
                }
                addView(
                    line,
                    LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
                        if (row == 0) bottomMargin = gap / 2 else topMargin = gap / 2
                    },
                )
            }
        }

        approvalText = label(sizeSp = 16f).apply { gravity = Gravity.CENTER_VERTICAL }
        approvalBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(12f), dp(8f), dp(8f), dp(8f))
            setBackgroundColor(getColor(R.color.panel))
            addView(approvalText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(flatButton(R.string.action_ignore) { answerPending(allow = false) })
            addView(flatButton(R.string.action_allow, primary = true) { answerPending(allow = true) }, buttonParams())
        }

        val appName = label(R.string.app_name_caps, sizeSp = 14f, secondary = true).apply {
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            letterSpacing = 0.08f
        }
        addressText = label(sizeSp = 18f).apply { typeface = Typeface.MONOSPACE }
        stateText = label(sizeSp = 15f, secondary = true)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(appName)
            addView(addressText)
            addView(stateText)
        }
        primaryButton = flatButton(R.string.action_start, primary = true) { onPrimary() }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(flatButton(R.string.action_settings) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
            addView(flatButton(R.string.action_diagnostics) { openDiagnostics() }, buttonParams())
            addView(primaryButton, buttonParams())
        }
        val statusBar = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(8f), dp(8f), dp(8f), 0)
            if (landscape) {
                addView(info, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(actions)
            } else {
                addView(info)
                addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8f) })
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.ground))
            padForSystemBars(extra = gap)
            addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(approvalBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = gap })
            addView(statusBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8f) }

    private fun render(state: BridgeState) {
        val status = DashboardModel.status(state)
        for ((slot, view) in slotViews.withIndex()) {
            val model = DashboardModel.slot(slot, state.slots[slot], streaming = status.running)
            val title = getString(titleOf(model.side))
            val line = getString(statusOf(model.look))
            val description = listOfNotNull(model.label, title, line, model.battery?.let(::batteryWords)).joinToString(", ")
            view.bind(model, title, line, description)
        }

        val address = status.address ?: getString(R.string.no_wifi_address)
        addressText.text = getString(R.string.address_and_port, address, status.port)
        val stateLine = when (status.serverText) {
            ServerText.STOPPED -> getString(R.string.state_stopped)
            ServerText.WAITING -> getString(R.string.state_waiting)
            ServerText.SENDING -> resources.getQuantityString(R.plurals.state_sending, status.emulatorCount, status.emulatorCount)
            ServerText.PORT_IN_USE -> getString(R.string.state_port_in_use, status.port)
            ServerText.NETWORK_ERROR -> getString(R.string.state_network_error)
        }
        stateText.text = if (status.slotsFull) getString(R.string.state_with_slots_full, stateLine) else stateLine
        primaryButton.setText(if (status.running) R.string.action_stop else R.string.action_start)

        shownPending = state.pendingClient
        approvalBar.visibility = if (shownPending == null) View.GONE else View.VISIBLE
        shownPending?.let { approvalText.text = getString(R.string.client_asks, it) }

        updateScreenTimeout(status.running)
    }

    private fun updateScreenTimeout(running: Boolean) {
        val keepOn = running && settings.keepScreenOn
        setKeepScreenOn(keepOn)
        val idle = SystemClock.uptimeMillis() - lastTouchMillis >= DIM_AFTER_MS
        if (keepOn && settings.dimWhenIdle && idle && !dimmed) setDimmed(true)
    }

    /** Window changes cost a relayout, so they are made only when the value changes. */
    private fun setKeepScreenOn(keepOn: Boolean) {
        if (screenKeptOn == keepOn) return
        screenKeptOn = keepOn
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setDimmed(dim: Boolean) {
        if (dimmed == dim) return
        dimmed = dim
        window.attributes = window.attributes.apply {
            screenBrightness = if (dim) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun onPrimary() {
        val bridge = connection.bridge ?: return
        if (bridge.isRunning) {
            connection.stopBridge()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Asked once per tap on Start; the bridge runs either way.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
        BridgeService.start(this)
    }

    private fun answerPending(allow: Boolean) {
        val bridge = connection.bridge ?: return
        val address = shownPending ?: return
        if (allow) bridge.approveClient(address) else bridge.ignoreClient(address)
        render(bridge.state())
    }

    private fun openDiagnosticsIfNoMotion(slot: Int) {
        val state = connection.bridge?.state() ?: return
        val model = DashboardModel.slot(slot, state.slots[slot], DashboardModel.status(state).running)
        if (model.look == SlotLook.NO_MOTION) openDiagnostics()
    }

    private fun openDiagnostics() = startActivity(Intent(this, DiagnosticsActivity::class.java))

    private fun batteryWords(battery: BatteryIcon): String {
        val level = getString(
            when (battery.segments) {
                4 -> R.string.battery_full
                3 -> R.string.battery_high
                2 -> R.string.battery_medium
                else -> R.string.battery_low
            },
        )
        return if (battery.charging) getString(R.string.battery_charging, level) else level
    }

    private companion object {
        const val REFRESH_MS = 100L
        const val DIM_AFTER_MS = 60_000L
        const val DIM_BRIGHTNESS = 0.05f
        const val GAP_DP = 4f
        const val NOTIFICATION_REQUEST = 1

        fun titleOf(side: JoyConSide?): Int = when (side) {
            null -> R.string.slot_free
            JoyConSide.LEFT -> R.string.slot_left
            JoyConSide.RIGHT -> R.string.slot_right
        }

        fun statusOf(look: SlotLook): Int = when (look) {
            SlotLook.FREE -> R.string.slot_pair_hint
            SlotLook.READY -> R.string.slot_ready
            SlotLook.MOTION -> R.string.slot_motion
            SlotLook.NO_MOTION -> R.string.slot_no_motion
        }
    }
}
