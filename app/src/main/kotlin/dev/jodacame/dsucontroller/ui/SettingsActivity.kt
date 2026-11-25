// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.service.AddClientResult
import dev.jodacame.dsucontroller.settings.BridgeSettings
import dev.jodacame.dsucontroller.settings.PortRule
import java.util.Locale

/** Allowed emulators, port and screen behaviour (docs/contracts/dashboard-ui.md, "Settings screen"). */
class SettingsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val connection: BridgeConnection = BridgeConnection(this) { refresh.run() }
    private lateinit var settings: BridgeSettings
    private lateinit var addressText: TextView
    private lateinit var clientList: LinearLayout
    private lateinit var clientField: EditText
    private lateinit var clientError: TextView
    private lateinit var portField: EditText
    private lateinit var portMessage: TextView
    private lateinit var notificationNote: TextView
    private var shownClients: Pair<List<String>, Set<String>>? = null

    private val refresh: Runnable = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            connection.bridge?.let { bridge ->
                val state = bridge.state()
                addressText.text = getString(
                    R.string.settings_this_phone,
                    state.localAddress ?: getString(R.string.no_wifi_address),
                    state.port,
                )
                val clients = state.approvedClients to state.subscribers
                if (clients != shownClients) {
                    shownClients = clients
                    renderClients(clients.first, clients.second)
                }
            }
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
        notificationNote.visibility = if (notificationsDenied()) View.VISIBLE else View.GONE
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        connection.unbind()
        super.onStop()
    }

    /** Joy-Con keys keep going to the bridge here too; B must not close the screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        connection.bridge?.onKeyEvent(event) == true || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        connection.bridge?.onMotionEvent(event) == true || super.dispatchGenericMotionEvent(event)

    private fun buildContent(): View {
        addressText = label(sizeSp = 18f).apply { typeface = Typeface.MONOSPACE }
        clientList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clientField = field(R.string.settings_add_hint).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            keyListener = DigitsKeyListener.getInstance("0123456789.")
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> addClient(); true }
        }
        clientError = label(sizeSp = 14f, secondary = true).apply { visibility = View.GONE }
        portField = field(0).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(portText(settings.port))
        }
        portMessage = label(sizeSp = 14f, secondary = true).apply { visibility = View.GONE }
        notificationNote = label(R.string.settings_notifications_off, sizeSp = 14f, secondary = true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(R.string.settings_title, sizeSp = 28f))
            addView(addressText, spaced(8f))
            addView(label(R.string.settings_this_phone_hint, sizeSp = 14f, secondary = true))

            addView(section(R.string.settings_clients), spaced(24f))
            addView(clientList)
            addView(row(clientField, flatButton(R.string.action_add, primary = true) { addClient() }), spaced(8f))
            addView(clientError)

            addView(section(R.string.settings_port), spaced(24f))
            addView(row(portField, flatButton(R.string.action_save) { savePort() }), spaced(8f))
            addView(portMessage)

            addView(section(R.string.settings_screen), spaced(24f))
            addView(switch(R.string.settings_keep_screen_on, settings.keepScreenOn) { settings.keepScreenOn = it })
            addView(switch(R.string.settings_dim, settings.dimWhenIdle) { settings.dimWhenIdle = it })
            addView(notificationNote, spaced(8f))

            addView(section(R.string.settings_about), spaced(24f))
            addView(label(sizeSp = 16f).apply { text = getString(R.string.settings_version, versionName()) })
            addView(label(R.string.settings_about_text, sizeSp = 14f, secondary = true), spaced(4f))
            addView(
                flatButton(R.string.action_diagnostics) { startActivity(Intent(this@SettingsActivity, DiagnosticsActivity::class.java)) },
                spaced(12f).apply { width = WRAP_CONTENT },
            )
        }
        val padding = dp(20f)
        return ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.ground))
            padForSystemBars(extra = padding)
            clipToPadding = false
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun renderClients(approved: List<String>, subscribed: Set<String>) {
        clientList.removeAllViews()
        if (approved.isEmpty()) {
            clientList.addView(label(R.string.settings_no_clients, sizeSp = 15f, secondary = true), spaced(8f))
            return
        }
        for (address in approved) {
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(sizeSp = 18f).apply { this.text = address; typeface = Typeface.MONOSPACE })
                if (address in subscribed) addView(label(R.string.settings_connected_now, sizeSp = 14f, secondary = true))
            }
            val remove = flatButton(R.string.action_remove) {
                connection.bridge?.removeClient(address)
                refresh.run()
            }
            clientList.addView(row(text, remove), spaced(8f))
        }
    }

    private fun addClient() {
        val bridge = connection.bridge ?: return
        val message = when (bridge.addClient(clientField.text.toString())) {
            AddClientResult.ADDED -> {
                clientField.text.clear()
                null
            }
            AddClientResult.INVALID -> getString(R.string.error_invalid_address)
            AddClientResult.ALREADY_ALLOWED -> getString(R.string.error_already_allowed)
        }
        clientError.text = message
        clientError.visibility = if (message == null) View.GONE else View.VISIBLE
        refresh.run()
    }

    private fun savePort() {
        val port = PortRule.parse(portField.text.toString())
        if (port == null) {
            portMessage.text = getString(R.string.error_port_range)
        } else {
            val bridge = connection.bridge
            if (bridge == null) settings.port = port else bridge.changePort(port)
            portField.setText(portText(port))
            portMessage.text = getString(R.string.settings_port_saved)
        }
        portMessage.visibility = View.VISIBLE
    }

    /** A port is a technical number: always plain ASCII digits, whatever the locale. */
    private fun portText(port: Int): String = String.format(Locale.ROOT, "%d", port)

    private fun notificationsDenied(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun section(textRes: Int): TextView = label(textRes, sizeSp = 14f, secondary = true).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        letterSpacing = 0.08f
        isAllCaps = true
    }

    private fun field(hintRes: Int): EditText = EditText(this).apply {
        if (hintRes != 0) setHint(hintRes)
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_secondary))
        textSize = 18f
        isSingleLine = true
        minHeight = dp(48f)
    }

    private fun switch(textRes: Int, checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        setText(textRes)
        textSize = 16f
        setTextColor(getColor(R.color.text_primary))
        minHeight = dp(48f)
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun row(main: View, action: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(action, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(12f) })
    }

    private fun spaced(topDp: Float) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(topDp) }

    private companion object {
        const val REFRESH_MS = 500L
    }
}
