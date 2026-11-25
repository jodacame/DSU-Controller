// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.hardware.BatteryState
import android.view.KeyEvent
import android.view.MotionEvent
import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.protocol.DsuButton
import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.protocol.PadFrame
import kotlin.math.roundToInt

/**
 * The single place that turns what Android reports for a Joy-Con into DSU fields
 * (docs/contracts/dsu-protocol.md, "Joy-Con to DSU mapping"). Nothing else in the app flips an
 * axis or renames a button.
 *
 * Android names the buttons by position, as on an Xbox pad, so the letters do not match the
 * ones printed on the Joy-Con: the key code `BUTTON_A` is the bottom button, which Nintendo
 * calls B. Each entry below names the physical Joy-Con button it stands for.
 */
object JoyConMapping {
    /** What the pressed keys of one Joy-Con add up to. */
    data class Buttons(val mask: Int, val home: Boolean, val capture: Boolean) {
        val anyPressed: Boolean get() = mask != 0 || home || capture
    }

    /** The two Android joystick axes of a Joy-Con's stick. */
    data class StickAxes(val x: Int, val y: Int)

    private sealed interface Target {
        data class Button(val mask: Int) : Target

        data object Home : Target

        data object Capture : Target
    }

    private val LEFT_KEYS: Map<Int, Target> = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to Target.Button(DsuButton.UP),
        KeyEvent.KEYCODE_DPAD_DOWN to Target.Button(DsuButton.DOWN),
        KeyEvent.KEYCODE_DPAD_LEFT to Target.Button(DsuButton.LEFT),
        KeyEvent.KEYCODE_DPAD_RIGHT to Target.Button(DsuButton.RIGHT),
        KeyEvent.KEYCODE_BUTTON_L1 to Target.Button(DsuButton.L1), // L
        KeyEvent.KEYCODE_BUTTON_L2 to Target.Button(DsuButton.L2), // ZL
        KeyEvent.KEYCODE_BUTTON_R1 to Target.Button(DsuButton.L2), // SL on the rail
        KeyEvent.KEYCODE_BUTTON_R2 to Target.Button(DsuButton.R2), // SR on the rail
        KeyEvent.KEYCODE_BUTTON_SELECT to Target.Button(DsuButton.SHARE), // Minus
        KeyEvent.KEYCODE_BUTTON_THUMBL to Target.Button(DsuButton.L3), // stick press
        KeyEvent.KEYCODE_BUTTON_MODE to Target.Capture,
    )

    private val RIGHT_KEYS: Map<Int, Target> = mapOf(
        KeyEvent.KEYCODE_BUTTON_B to Target.Button(DsuButton.CIRCLE), // A (right)
        KeyEvent.KEYCODE_BUTTON_A to Target.Button(DsuButton.CROSS), // B (bottom)
        KeyEvent.KEYCODE_BUTTON_Y to Target.Button(DsuButton.TRIANGLE), // X (top)
        KeyEvent.KEYCODE_BUTTON_X to Target.Button(DsuButton.SQUARE), // Y (left)
        KeyEvent.KEYCODE_BUTTON_R1 to Target.Button(DsuButton.R1), // R
        KeyEvent.KEYCODE_BUTTON_R2 to Target.Button(DsuButton.R2), // ZR
        KeyEvent.KEYCODE_BUTTON_L1 to Target.Button(DsuButton.L2), // SL on the rail
        KeyEvent.KEYCODE_BUTTON_L2 to Target.Button(DsuButton.R2), // SR on the rail
        KeyEvent.KEYCODE_BUTTON_START to Target.Button(DsuButton.OPTIONS), // Plus
        KeyEvent.KEYCODE_BUTTON_THUMBR to Target.Button(DsuButton.R3), // stick press
        KeyEvent.KEYCODE_HOME to Target.Home,
    )

    private const val STANDARD_GRAVITY = 9.80665f
    private const val DEGREES_PER_RADIAN = 57.29578f

    private const val BATTERY_FULL = 0.85f
    private const val BATTERY_HIGH = 0.6f
    private const val BATTERY_MEDIUM = 0.4f
    private const val BATTERY_LOW = 0.15f

    /**
     * Combines the Android key codes currently held on a Joy-Con. Two physical buttons can share
     * one DSU button (ZL and SL are both L2), so the result is computed from the whole set.
     */
    fun buttons(side: JoyConSide, pressedKeyCodes: Iterable<Int>): Buttons {
        val keys = keysOf(side)
        var mask = 0
        var home = false
        var capture = false
        for (keyCode in pressedKeyCodes) {
            when (val target = keys[keyCode]) {
                is Target.Button -> mask = mask or target.mask
                Target.Home -> home = true
                Target.Capture -> capture = true
                null -> Unit
            }
        }
        return Buttons(mask, home, capture)
    }

    fun stickAxes(side: JoyConSide): StickAxes = when (side) {
        JoyConSide.LEFT -> StickAxes(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
        JoyConSide.RIGHT -> StickAxes(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
    }

    /** Android stick X (-1 left to 1 right) as a DSU stick byte. */
    fun stickXByte(value: Float): Int = stickByte(value)

    /** Android stick Y (-1 up to 1 down) as a DSU stick byte, where up is the high end. */
    fun stickYByte(value: Float): Int = stickByte(-value)

    /**
     * Builds the wire frame of one Joy-Con.
     *
     * Motion: Android delivers the Joy-Con's own axes for a controller held upright, in m/s² and
     * rad/s: X towards the shoulder buttons, Y to the left, Z out of the face with the buttons,
     * the same for both sides. The DSU client expects a full controller's frame: accelerometer
     * X to the left, Y down and Z away from the player, and gyroscope pitch about the axis
     * to the right, yaw about the axis down and roll about the axis away from the player.
     *
     * @param stickX the Joy-Con's stick as DSU bytes; it fills the left pair for a left Joy-Con
     *   and the right pair for a right one
     */
    fun frame(
        side: JoyConSide,
        buttons: Buttons,
        stickX: Int,
        stickY: Int,
        timestampMicros: Long,
        accelerometer: FloatArray,
        gyroscope: FloatArray,
    ): PadFrame {
        val left = side == JoyConSide.LEFT
        return PadFrame(
            buttons = buttons.mask,
            home = buttons.home,
            touchHardPress = buttons.capture,
            leftStickX = if (left) stickX else DsuProtocol.STICK_NEUTRAL,
            leftStickY = if (left) stickY else DsuProtocol.STICK_NEUTRAL,
            rightStickX = if (left) DsuProtocol.STICK_NEUTRAL else stickX,
            rightStickY = if (left) DsuProtocol.STICK_NEUTRAL else stickY,
            timestampMicros = timestampMicros,
            accelX = accelerometer[1] / STANDARD_GRAVITY,
            accelY = -accelerometer[2] / STANDARD_GRAVITY,
            accelZ = accelerometer[0] / STANDARD_GRAVITY,
            gyroPitch = -gyroscope[1] * DEGREES_PER_RADIAN,
            gyroYaw = -gyroscope[2] * DEGREES_PER_RADIAN,
            gyroRoll = gyroscope[0] * DEGREES_PER_RADIAN,
        )
    }

    /**
     * @param capacity Android's battery level from 0 to 1, NaN when unknown
     * @param status one of the `BatteryState.STATUS_*` values
     */
    fun battery(present: Boolean, capacity: Float, status: Int): DsuBattery = when {
        !present -> DsuBattery.NONE
        status == BatteryState.STATUS_FULL -> DsuBattery.CHARGED
        status == BatteryState.STATUS_CHARGING -> DsuBattery.CHARGING
        else -> batteryLevel(present, capacity)
    }

    /** The charge level alone, whether or not the controller is charging. */
    fun batteryLevel(present: Boolean, capacity: Float): DsuBattery = when {
        !present || capacity.isNaN() -> DsuBattery.NONE
        capacity >= BATTERY_FULL -> DsuBattery.FULL
        capacity >= BATTERY_HIGH -> DsuBattery.HIGH
        capacity >= BATTERY_MEDIUM -> DsuBattery.MEDIUM
        capacity >= BATTERY_LOW -> DsuBattery.LOW
        else -> DsuBattery.DYING
    }

    private fun keysOf(side: JoyConSide): Map<Int, Target> = when (side) {
        JoyConSide.LEFT -> LEFT_KEYS
        JoyConSide.RIGHT -> RIGHT_KEYS
    }

    private fun stickByte(value: Float): Int =
        (DsuProtocol.STICK_NEUTRAL + value * DsuProtocol.STICK_NEUTRAL)
            .roundToInt()
            .coerceIn(0, DsuProtocol.STICK_MAX)
}
