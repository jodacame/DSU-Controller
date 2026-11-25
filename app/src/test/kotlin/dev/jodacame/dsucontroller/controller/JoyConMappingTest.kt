// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.hardware.BatteryState
import android.view.KeyEvent
import android.view.MotionEvent
import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.protocol.DsuButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoyConMappingTest {
    private val noButtons = JoyConMapping.Buttons(0, home = false, capture = false)
    private val still = FloatArray(3)

    @Test
    fun mapsEveryRightJoyConButtonToTheDsuInputOfItsNintendoName() {
        val expected = mapOf(
            KeyEvent.KEYCODE_BUTTON_B to DsuButton.CIRCLE, // A
            KeyEvent.KEYCODE_BUTTON_A to DsuButton.CROSS, // B
            KeyEvent.KEYCODE_BUTTON_Y to DsuButton.TRIANGLE, // X
            KeyEvent.KEYCODE_BUTTON_X to DsuButton.SQUARE, // Y
            KeyEvent.KEYCODE_BUTTON_R1 to DsuButton.R1, // R
            KeyEvent.KEYCODE_BUTTON_R2 to DsuButton.R2, // ZR
            KeyEvent.KEYCODE_BUTTON_L1 to DsuButton.L2, // SL
            KeyEvent.KEYCODE_BUTTON_L2 to DsuButton.R2, // SR
            KeyEvent.KEYCODE_BUTTON_START to DsuButton.OPTIONS, // Plus
            KeyEvent.KEYCODE_BUTTON_THUMBR to DsuButton.R3,
        )

        for ((keyCode, mask) in expected) {
            assertEquals("key code $keyCode", mask, JoyConMapping.buttons(JoyConSide.RIGHT, listOf(keyCode)).mask)
        }
    }

    @Test
    fun mapsEveryLeftJoyConButtonToTheDsuInputOfItsNintendoName() {
        val expected = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to DsuButton.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to DsuButton.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to DsuButton.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to DsuButton.RIGHT,
            KeyEvent.KEYCODE_BUTTON_L1 to DsuButton.L1, // L
            KeyEvent.KEYCODE_BUTTON_L2 to DsuButton.L2, // ZL
            KeyEvent.KEYCODE_BUTTON_R1 to DsuButton.L2, // SL
            KeyEvent.KEYCODE_BUTTON_R2 to DsuButton.R2, // SR
            KeyEvent.KEYCODE_BUTTON_SELECT to DsuButton.SHARE, // Minus
            KeyEvent.KEYCODE_BUTTON_THUMBL to DsuButton.L3,
        )

        for ((keyCode, mask) in expected) {
            assertEquals("key code $keyCode", mask, JoyConMapping.buttons(JoyConSide.LEFT, listOf(keyCode)).mask)
        }
    }

    @Test
    fun homeAndCaptureHaveTheirOwnFields() {
        assertTrue(JoyConMapping.buttons(JoyConSide.RIGHT, listOf(KeyEvent.KEYCODE_HOME)).home)
        assertTrue(JoyConMapping.buttons(JoyConSide.LEFT, listOf(KeyEvent.KEYCODE_BUTTON_MODE)).capture)
    }

    @Test
    fun ignoresKeysTheSideDoesNotHave() {
        val buttons = JoyConMapping.buttons(JoyConSide.LEFT, listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE))

        assertFalse(buttons.anyPressed)
    }

    @Test
    fun readsTheStickFromTheAxesOfEachSide() {
        assertEquals(JoyConMapping.StickAxes(MotionEvent.AXIS_X, MotionEvent.AXIS_Y), JoyConMapping.stickAxes(JoyConSide.LEFT))
        assertEquals(JoyConMapping.StickAxes(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ), JoyConMapping.stickAxes(JoyConSide.RIGHT))
    }

    @Test
    fun stickBytesAreNeutralAt127AndUpIsTheHighEnd() {
        assertEquals(127, JoyConMapping.stickXByte(0f))
        assertEquals(254, JoyConMapping.stickXByte(1f))
        assertEquals(0, JoyConMapping.stickXByte(-1f))
        assertEquals(254, JoyConMapping.stickYByte(-1f))
        assertEquals(0, JoyConMapping.stickYByte(1f))
        assertEquals(254, JoyConMapping.stickXByte(1.2f))
    }

    @Test
    fun aLeftJoyConFillsTheLeftStickAndARightJoyConTheRightStick() {
        val left = JoyConMapping.frame(JoyConSide.LEFT, noButtons, 10, 20, 0, still, still)
        val right = JoyConMapping.frame(JoyConSide.RIGHT, noButtons, 10, 20, 0, still, still)

        assertEquals(listOf(10, 20, 127, 127), listOf(left.leftStickX, left.leftStickY, left.rightStickX, left.rightStickY))
        assertEquals(listOf(127, 127, 10, 20), listOf(right.leftStickX, right.leftStickY, right.rightStickX, right.rightStickY))
    }

    @Test
    fun aJoyConLyingFaceUpReportsOneGDownwardsOnTheWire() {
        val faceUp = floatArrayOf(0f, 0f, 9.80665f)

        val frame = JoyConMapping.frame(JoyConSide.LEFT, noButtons, 127, 127, 0, faceUp, still)

        assertEquals(0f, frame.accelX, 1e-6f)
        assertEquals(-1f, frame.accelY, 1e-6f)
        assertEquals(0f, frame.accelZ, 1e-6f)
    }

    @Test
    fun convertsEachGyroscopeAxisToDegreesPerSecond() {
        val oneRadianPerSecond = 57.29578f
        fun frameFor(x: Float, y: Float, z: Float) =
            JoyConMapping.frame(JoyConSide.RIGHT, noButtons, 127, 127, 0, still, floatArrayOf(x, y, z))

        assertEquals(oneRadianPerSecond, frameFor(1f, 0f, 0f).gyroRoll, 1e-3f)
        assertEquals(-oneRadianPerSecond, frameFor(0f, 1f, 0f).gyroPitch, 1e-3f)
        assertEquals(-oneRadianPerSecond, frameFor(0f, 0f, 1f).gyroYaw, 1e-3f)
    }

    @Test
    fun mapsAndroidBatteryStatesToDsuLevels() {
        val discharging = BatteryState.STATUS_DISCHARGING

        assertEquals(DsuBattery.NONE, JoyConMapping.battery(present = false, capacity = 1f, status = discharging))
        assertEquals(DsuBattery.NONE, JoyConMapping.battery(present = true, capacity = Float.NaN, status = discharging))
        assertEquals(DsuBattery.FULL, JoyConMapping.battery(true, 1f, discharging))
        assertEquals(DsuBattery.HIGH, JoyConMapping.battery(true, 0.7f, discharging))
        assertEquals(DsuBattery.MEDIUM, JoyConMapping.battery(true, 0.55f, discharging))
        assertEquals(DsuBattery.LOW, JoyConMapping.battery(true, 0.25f, discharging))
        assertEquals(DsuBattery.DYING, JoyConMapping.battery(true, 0.05f, discharging))
        assertEquals(DsuBattery.CHARGING, JoyConMapping.battery(true, 0.3f, BatteryState.STATUS_CHARGING))
        assertEquals(DsuBattery.CHARGED, JoyConMapping.battery(true, 1f, BatteryState.STATUS_FULL))
    }
}
