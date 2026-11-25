// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SampleRateMeterTest {
    private val fiveMillisNanos = 5_000_000L

    @Test
    fun reportsZeroBeforeAFullWindow() {
        val meter = SampleRateMeter()

        meter.onSample(0)
        meter.onSample(fiveMillisNanos)

        assertEquals(0.0, meter.ratePerSecond, 0.0)
        assertEquals(2, meter.totalSamples)
    }

    @Test
    fun measuresASteadyTwoHundredHertzStream() {
        val meter = SampleRateMeter()

        for (i in 0..200) meter.onSample(i * fiveMillisNanos)

        assertEquals(200.0, meter.ratePerSecond, 0.001)
    }

    @Test
    fun followsARateChangeInTheNextWindow() {
        val meter = SampleRateMeter()
        for (i in 0..200) meter.onSample(i * fiveMillisNanos)
        val secondWindowStart = 200 * fiveMillisNanos

        for (i in 1..100) meter.onSample(secondWindowStart + i * 2 * fiveMillisNanos)

        assertEquals(100.0, meter.ratePerSecond, 0.001)
    }

    @Test
    fun keepsTheLastRateWhenTheStreamStalls() {
        val meter = SampleRateMeter()
        for (i in 0..200) meter.onSample(i * fiveMillisNanos)

        assertEquals(200.0, meter.ratePerSecond, 0.001)
        assertEquals(200 * fiveMillisNanos, meter.lastTimestampNanos)
    }

    @Test
    fun rejectsANonPositiveWindow() {
        assertThrows(IllegalArgumentException::class.java) { SampleRateMeter(windowNanos = 0) }
    }
}
