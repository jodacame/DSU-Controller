// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

/**
 * Measures how many samples per second a stream delivers, from the timestamps it is given.
 *
 * The rate is recomputed once per window, so a stalled stream keeps reporting its last rate;
 * callers that care about staleness compare [lastTimestampNanos] with the current time.
 * Not thread-safe: the owner serialises access.
 */
class SampleRateMeter(private val windowNanos: Long = NANOS_PER_SECOND) {
    private var windowStartNanos = NO_TIMESTAMP
    private var samplesInWindow = 0

    /** Samples per second over the last complete window; zero until one window has passed. */
    var ratePerSecond: Double = 0.0
        private set

    /** Number of samples seen since creation. */
    var totalSamples: Long = 0
        private set

    /** Timestamp of the most recent sample, or [NO_TIMESTAMP] before the first one. */
    var lastTimestampNanos: Long = NO_TIMESTAMP
        private set

    init {
        require(windowNanos > 0) { "windowNanos must be positive, was $windowNanos" }
    }

    fun onSample(timestampNanos: Long) {
        totalSamples++
        lastTimestampNanos = timestampNanos
        if (windowStartNanos == NO_TIMESTAMP) {
            windowStartNanos = timestampNanos
            return
        }
        samplesInWindow++
        val elapsedNanos = timestampNanos - windowStartNanos
        if (elapsedNanos >= windowNanos) {
            ratePerSecond = samplesInWindow * NANOS_PER_SECOND.toDouble() / elapsedNanos
            windowStartNanos = timestampNanos
            samplesInWindow = 0
        }
    }

    companion object {
        const val NO_TIMESTAMP = -1L
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
