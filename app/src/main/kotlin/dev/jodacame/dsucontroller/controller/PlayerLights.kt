// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.graphics.Color
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.view.InputDevice

/**
 * Which of the four player LEDs of a Joy-Con are lit for a player number.
 *
 * Android exposes each LED as its own light whose name ends in `player-N`; the same naming
 * carries a fifth light (the Home button ring) that is not a player LED.
 */
object PlayerLightPattern {
    const val MAX_PLAYER = 4
    private val LED_NAME = Regex("""player-(\d+)$""")

    /** Returns the LED position (1 to 4) a light name stands for, or null for any other light. */
    fun ledIndex(lightName: String): Int? {
        val index = LED_NAME.find(lightName)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return index.takeIf { it in 1..MAX_PLAYER }
    }

    /** The console's own convention: player N lights the first N LEDs. */
    fun isLit(ledIndex: Int, player: Int): Boolean {
        require(player in 1..MAX_PLAYER) { "player must be 1..$MAX_PLAYER, was $player" }
        return ledIndex in 1..player
    }
}

/**
 * Shows a player number on the LEDs of one Joy-Con. Closing releases the LEDs, which turns them
 * off: the platform does not restore what they showed before.
 */
class PlayerLights(device: InputDevice) : AutoCloseable {
    private val leds: Map<Int, Light> = device.lightsManager.lights
        .mapNotNull { light -> PlayerLightPattern.ledIndex(light.name)?.let { it to light } }
        .toMap()
    private val lightsManager = device.lightsManager

    /**
     * Opened on the first request: on the reference phone, closing a session that never
     * requested a light makes the system service throw.
     */
    private var session: LightsManager.LightsSession? = null

    /** False when this phone exposes no player LEDs for the controller. */
    val available: Boolean get() = leds.isNotEmpty()

    fun show(player: Int) {
        if (!available) return
        val request = LightsRequest.Builder()
        for ((index, light) in leds) {
            val color = if (PlayerLightPattern.isLit(index, player)) Color.WHITE else Color.TRANSPARENT
            request.addLight(light, LightState.Builder().setColor(color).build())
        }
        val current = session ?: lightsManager.openSession().also { session = it }
        current.requestLights(request.build())
    }

    override fun close() {
        session?.close()
        session = null
    }
}
