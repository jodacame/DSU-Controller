// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.protocol.PadFrame

/**
 * The live input of one Joy-Con: held keys, stick and the latest motion sample. Written from the
 * main thread (keys, stick) and the sensor thread (motion), read by whoever sends; thread-safe.
 */
class PadState(private val side: JoyConSide) {
    private val lock = Any()
    private val pressedKeyCodes = HashSet<Int>()
    private var buttons = JoyConMapping.Buttons(mask = 0, home = false, capture = false)
    private var stickX = DsuProtocol.STICK_NEUTRAL
    private var stickY = DsuProtocol.STICK_NEUTRAL
    private val accelerometer = FloatArray(AXES)
    private val gyroscope = FloatArray(AXES)
    private var timestampMicros = 0L

    val anyButtonPressed: Boolean get() = synchronized(lock) { buttons.anyPressed }

    /** Returns true when the key changed what this Joy-Con sends. */
    fun onKey(keyCode: Int, pressed: Boolean): Boolean = synchronized(lock) {
        val changed = if (pressed) pressedKeyCodes.add(keyCode) else pressedKeyCodes.remove(keyCode)
        if (!changed) return false
        val updated = JoyConMapping.buttons(side, pressedKeyCodes)
        (updated != buttons).also { buttons = updated }
    }

    /** @param x Android's stick axes, -1 to 1 */
    fun onStick(x: Float, y: Float): Unit = synchronized(lock) {
        stickX = JoyConMapping.stickXByte(x)
        stickY = JoyConMapping.stickYByte(y)
    }

    fun onAccelerometer(x: Float, y: Float, z: Float, arrivalMicros: Long): Unit = synchronized(lock) {
        store(accelerometer, x, y, z, arrivalMicros)
    }

    fun onGyroscope(x: Float, y: Float, z: Float, arrivalMicros: Long): Unit = synchronized(lock) {
        store(gyroscope, x, y, z, arrivalMicros)
    }

    fun frame(): PadFrame = synchronized(lock) {
        JoyConMapping.frame(side, buttons, stickX, stickY, timestampMicros, accelerometer, gyroscope)
    }

    private fun store(target: FloatArray, x: Float, y: Float, z: Float, arrivalMicros: Long) {
        target[0] = x
        target[1] = y
        target[2] = z
        // The wire timestamp never goes backwards within a session.
        if (arrivalMicros > timestampMicros) timestampMicros = arrivalMicros
    }

    private companion object {
        const val AXES = 3
    }
}
