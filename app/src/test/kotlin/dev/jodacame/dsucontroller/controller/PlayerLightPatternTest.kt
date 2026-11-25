// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerLightPatternTest {
    @Test
    fun readsTheLedPositionFromTheLightName() {
        assertEquals(3, PlayerLightPattern.ledIndex("0005:057E:2006.0002:green:player-3"))
    }

    @Test
    fun ignoresTheHomeButtonLight() {
        assertNull(PlayerLightPattern.ledIndex("0005:057E:2007.0001:blue:player-5"))
    }

    @Test
    fun ignoresLightsThatAreNotPlayerLeds() {
        assertNull(PlayerLightPattern.ledIndex("keyboard-backlight"))
    }

    @Test
    fun lightsTheFirstLedsUpToThePlayerNumber() {
        val litForPlayerThree = (1..4).filter { PlayerLightPattern.isLit(it, player = 3) }

        assertEquals(listOf(1, 2, 3), litForPlayerThree)
    }

    @Test
    fun rejectsAPlayerOutsideTheFourSlots() {
        assertThrows(IllegalArgumentException::class.java) { PlayerLightPattern.isLit(1, player = 5) }
    }
}
