// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.view.KeyEvent
import dev.jodacame.dsucontroller.protocol.DsuButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadStateTest {
    private val state = PadState(JoyConSide.LEFT)

    @Test
    fun startsNeutral() {
        val frame = state.frame()

        assertEquals(0, frame.buttons)
        assertEquals(127, frame.leftStickX)
        assertEquals(127, frame.leftStickY)
        assertFalse(state.anyButtonPressed)
    }

    @Test
    fun reportsAKeyPressAndItsRelease() {
        assertTrue(state.onKey(KeyEvent.KEYCODE_BUTTON_L1, pressed = true))
        assertEquals(DsuButton.L1, state.frame().buttons)

        assertTrue(state.onKey(KeyEvent.KEYCODE_BUTTON_L1, pressed = false))
        assertEquals(0, state.frame().buttons)
    }

    @Test
    fun keyRepeatsAndUnknownKeysChangeNothing() {
        state.onKey(KeyEvent.KEYCODE_BUTTON_L1, pressed = true)

        assertFalse(state.onKey(KeyEvent.KEYCODE_BUTTON_L1, pressed = true))
        assertFalse(state.onKey(KeyEvent.KEYCODE_SPACE, pressed = true))
    }

    @Test
    fun twoButtonsSharingOneDsuButtonKeepItHeldUntilBothAreReleased() {
        state.onKey(KeyEvent.KEYCODE_BUTTON_L2, pressed = true) // ZL
        state.onKey(KeyEvent.KEYCODE_BUTTON_R1, pressed = true) // SL, also L2

        state.onKey(KeyEvent.KEYCODE_BUTTON_L2, pressed = false)
        assertEquals(DsuButton.L2, state.frame().buttons)

        state.onKey(KeyEvent.KEYCODE_BUTTON_R1, pressed = false)
        assertEquals(0, state.frame().buttons)
    }

    @Test
    fun theTimestampNeverGoesBackwards() {
        state.onGyroscope(0f, 0f, 0f, arrivalMicros = 2_000)
        state.onAccelerometer(0f, 0f, 0f, arrivalMicros = 1_500)

        assertEquals(2_000, state.frame().timestampMicros)
    }

    @Test
    fun keepsTheLatestStickPosition() {
        state.onStick(x = 1f, y = -1f)

        assertEquals(254, state.frame().leftStickX)
        assertEquals(254, state.frame().leftStickY)
    }
}
