// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import dev.jodacame.dsucontroller.controller.MotionStall.NEVER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionStallTest {
    private val second = 1_000_000_000L
    private val start = 50 * second

    @Test
    fun noSampleForASecondAfterRegisteringIsAStall() {
        assertFalse(MotionStall.stalled(start + second - 1, lastSample = NEVER, registeredSince = start))
        assertTrue(MotionStall.stalled(start + second, lastSample = NEVER, registeredSince = start))
    }

    @Test
    fun aSecondAfterTheLastSampleIsAStall() {
        val sample = start + 3 * second
        assertFalse(MotionStall.stalled(sample + second - 1, lastSample = sample, registeredSince = start))
        assertTrue(MotionStall.stalled(sample + second, lastSample = sample, registeredSince = start))
    }

    @Test
    fun aRestartGivesTheSensorsAFullSecondAgain() {
        val sample = start
        val restart = start + 2 * second
        assertFalse(MotionStall.stalled(restart + second / 2, lastSample = sample, registeredSince = restart))
    }

    @Test
    fun motionIsFreshForTwoSecondsAfterASample() {
        assertTrue(MotionStall.fresh(start + 2 * second - 1, lastSample = start))
        assertFalse(MotionStall.fresh(start + 2 * second, lastSample = start))
        assertFalse(MotionStall.fresh(start, lastSample = NEVER))
    }
}
