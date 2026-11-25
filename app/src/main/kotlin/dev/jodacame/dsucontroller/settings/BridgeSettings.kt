// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.settings

import android.content.Context
import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.server.Ipv4Address

/** The rule for a usable UDP port: unprivileged and valid. */
object PortRule {
    const val MIN = 1024
    const val MAX = 65535

    fun isValid(port: Int): Boolean = port in MIN..MAX

    /** Returns the port typed by the user, or null when it is not a number in range. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::isValid)
}

/**
 * The user's settings, stored on the device only (backup is off). Values are validated when
 * read, so a damaged file falls back to the safe default instead of reaching the bridge.
 */
class BridgeSettings(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var port: Int
        get() = preferences.getInt(KEY_PORT, DsuProtocol.DEFAULT_PORT).takeIf(PortRule::isValid)
            ?: DsuProtocol.DEFAULT_PORT
        set(value) {
            require(PortRule.isValid(value)) { "Port out of range: $value" }
            preferences.edit().putInt(KEY_PORT, value).apply()
        }

    /** Addresses the user allowed. Empty on a fresh install. */
    var approvedClients: List<String>
        get() = preferences.getStringSet(KEY_APPROVED, emptySet()).orEmpty()
            .mapNotNull(Ipv4Address::normalize)
            .sorted()
        set(value) = preferences.edit().putStringSet(KEY_APPROVED, value.toSet()).apply()

    var keepScreenOn: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var dimWhenIdle: Boolean
        get() = preferences.getBoolean(KEY_DIM_WHEN_IDLE, true)
        set(value) = preferences.edit().putBoolean(KEY_DIM_WHEN_IDLE, value).apply()

    private companion object {
        const val FILE_NAME = "bridge"
        const val KEY_PORT = "port"
        const val KEY_APPROVED = "approved_clients"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_DIM_WHEN_IDLE = "dim_when_idle"
    }
}
