// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

/**
 * Who may receive controller data: the addresses the user approved, the ones waiting for an
 * answer and the ones the user ignored for this run. Thread-safe.
 *
 * Nothing is approved unless [approve] is called; there is no default and no wildcard.
 */
class ClientAccess(approved: Collection<String> = emptyList()) {
    private val lock = Any()
    private val approvedAddresses = LinkedHashSet(approved)
    private val pendingAddresses = ArrayList<String>()
    private val ignoredAddresses = HashSet<String>()

    fun isApproved(address: String): Boolean = synchronized(lock) { address in approvedAddresses }

    /** Returns false when the address was already approved. */
    fun approve(address: String): Boolean = synchronized(lock) {
        pendingAddresses.remove(address)
        ignoredAddresses.remove(address)
        approvedAddresses.add(address)
    }

    fun remove(address: String): Unit = synchronized(lock) { approvedAddresses.remove(address) }

    /** Hides a pending address until the app restarts. */
    fun ignore(address: String): Unit = synchronized(lock) {
        pendingAddresses.remove(address)
        if (ignoredAddresses.size < MAX_IGNORED) ignoredAddresses.add(address)
    }

    /**
     * Records an address that sent a valid request without being approved. The list is bounded,
     * so a noisy network cannot grow it; extra addresses are dropped until there is room.
     */
    fun offerPending(address: String): Unit = synchronized(lock) {
        val known = address in approvedAddresses || address in ignoredAddresses || address in pendingAddresses
        if (!known && pendingAddresses.size < MAX_PENDING) pendingAddresses.add(address)
    }

    fun approved(): List<String> = synchronized(lock) { approvedAddresses.toList() }

    fun pending(): List<String> = synchronized(lock) { pendingAddresses.toList() }

    companion object {
        const val MAX_PENDING = 8
        private const val MAX_IGNORED = 64
    }
}
