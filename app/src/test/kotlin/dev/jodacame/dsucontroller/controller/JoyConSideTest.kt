// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoyConSideTest {
    @Test
    fun recognisesTheLeftJoyCon() {
        assertEquals(JoyConSide.LEFT, JoyConSide.fromIds(0x057E, 0x2006))
    }

    @Test
    fun recognisesTheRightJoyCon() {
        assertEquals(JoyConSide.RIGHT, JoyConSide.fromIds(0x057E, 0x2007))
    }

    @Test
    fun rejectsOtherNintendoControllers() {
        // 0x2009 is the Pro Controller: same vendor, not a Joy-Con.
        assertNull(JoyConSide.fromIds(0x057E, 0x2009))
    }

    @Test
    fun rejectsAJoyConProductIdFromAnotherVendor() {
        assertNull(JoyConSide.fromIds(0x054C, 0x2006))
    }
}
