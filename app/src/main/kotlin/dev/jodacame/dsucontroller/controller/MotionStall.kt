// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

/**
 * When the motion of a Joy-Con counts as stalled or as arriving. All times are elapsed-realtime
 * nanoseconds; [NEVER] stands for "no sample yet".
 *
 * A connected Joy-Con sends motion about 250 times a second, even lying still, so a second of
 * silence is never the controller. It is a platform fault: Android's input reader restarts its
 * sensor clock but keeps the time of the last sample, which the Joy-Con's fast clock had pushed
 * into the future, and drops every new sample until the clock catches up
 * (docs/architecture.md). Turning the sensors off clears that time.
 */
object MotionStall {
    const val NEVER = Long.MIN_VALUE / 2
    const val STALL_NANOS = 1_000_000_000L

    /** Longer than a stall plus its restart, so a recovered stall does not flash the warning. */
    const val FRESH_NANOS = 2_000_000_000L

    /** True when the sensors were registered for [STALL_NANOS] and no sample came since. */
    fun stalled(now: Long, lastSample: Long, registeredSince: Long): Boolean =
        now - maxOf(lastSample, registeredSince) >= STALL_NANOS

    /** True when a sample came in the last [FRESH_NANOS]. */
    fun fresh(now: Long, lastSample: Long): Boolean = now - lastSample < FRESH_NANOS
}
