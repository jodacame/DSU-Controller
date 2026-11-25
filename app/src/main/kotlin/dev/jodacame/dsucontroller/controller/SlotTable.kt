// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import java.security.MessageDigest

/**
 * Gives each connected controller one of the four slots.
 *
 * A controller keeps its slot while it stays connected, and gets the same slot back when it
 * reconnects and that slot is still free. Not thread-safe: the owner calls it from one thread.
 */
class SlotTable(private val slotCount: Int) {
    private val occupants = arrayOfNulls<String>(slotCount)
    private val previousSlots = object : LinkedHashMap<String, Int>() {
        override fun removeEldestEntry(eldest: Map.Entry<String, Int>): Boolean = size > REMEMBERED_CONTROLLERS
    }

    /** Returns the slot of [controllerId], assigning one if needed; null when all are taken. */
    fun assign(controllerId: String): Int? {
        slotOf(controllerId)?.let { return it }
        val remembered = previousSlots[controllerId]?.takeIf { occupants[it] == null }
        val slot = remembered ?: occupants.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return null
        occupants[slot] = controllerId
        previousSlots[controllerId] = slot
        return slot
    }

    fun release(controllerId: String) {
        slotOf(controllerId)?.let { occupants[it] = null }
    }

    fun slotOf(controllerId: String): Int? = occupants.indexOf(controllerId).takeIf { it >= 0 }

    companion object {
        private const val REMEMBERED_CONTROLLERS = 16
        private const val MAC_BYTES = 6

        /**
         * A stable 48-bit id for the DSU "MAC address" field, derived from Android's device
         * descriptor so the controller's real Bluetooth address never leaves the phone.
         */
        fun stableMac(descriptor: String): Long {
            val digest = MessageDigest.getInstance("SHA-256").digest(descriptor.toByteArray(Charsets.UTF_8))
            return digest.take(MAC_BYTES).fold(0L) { mac, byte -> (mac shl Byte.SIZE_BITS) or (byte.toLong() and 0xFF) }
        }
    }
}
