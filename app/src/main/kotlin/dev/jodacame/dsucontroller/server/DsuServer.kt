// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

import dev.jodacame.dsucontroller.protocol.DsuCodec
import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.protocol.DsuRequest
import dev.jodacame.dsucontroller.protocol.PadFilter
import dev.jodacame.dsucontroller.protocol.PadFrame
import dev.jodacame.dsucontroller.protocol.SlotInfo
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** Where the server reads the controllers from. Implementations are thread-safe. */
interface PadSource {
    fun slotInfo(slot: Int): SlotInfo

    /** Current input of a slot, or null when the slot is empty. */
    fun frame(slot: Int): PadFrame?
}

/**
 * The DSU server: the only socket of the app.
 *
 * It answers version and port info requests, keeps the clients that asked for pad data for
 * [SUBSCRIPTION_SECONDS] after their last request, and sends them a pad data packet on every
 * [publish]. A datagram from an address that [access] has not approved is never answered.
 *
 * [start] and [stop] are called from one thread; [publish] may be called from any thread.
 */
class DsuServer(
    private val pads: PadSource,
    private val access: ClientAccess,
    private val nanoTime: () -> Long = System::nanoTime,
    private val serverId: Int = Random.nextInt(),
) {
    private class Subscriber(val address: String, now: Long) {
        /** Per slot, the time until which this client wants its data. */
        val expiry = LongArray(DsuProtocol.SLOT_COUNT) { now }

        fun wants(slot: Int, now: Long): Boolean = expiry[slot] - now > 0

        fun isExpired(now: Long): Boolean = expiry.none { it - now > 0 }
    }

    private val lock = Any()
    private val subscribers = HashMap<InetSocketAddress, Subscriber>()
    private val packetCounters = IntArray(DsuProtocol.SLOT_COUNT)
    private val failedSends = AtomicLong()
    private var receiveThread: Thread? = null

    @Volatile
    private var socket: DatagramSocket? = null

    /** Called on the receive thread when the socket fails while the server is running. */
    @Volatile
    var onFailure: (IOException) -> Unit = {}

    val isRunning: Boolean get() = socket != null

    /** The bound port, or -1 while stopped. */
    val localPort: Int get() = socket?.localPort ?: -1

    /** Datagrams the network refused, for example while Wi-Fi is down. */
    val failedSendCount: Long get() = failedSends.get()

    /**
     * Binds the socket and starts answering.
     *
     * @throws SocketException when the port cannot be bound, for example because it is in use
     */
    @Throws(SocketException::class)
    fun start(port: Int) {
        check(socket == null) { "The server is already running" }
        // A socket failure leaves the old table behind; a new run starts with no subscriber.
        synchronized(lock) { subscribers.clear() }
        val newSocket = DatagramSocket(null)
        try {
            // DSCP EF: Wi-Fi sends the datagrams in its voice queue, ahead of bulk traffic.
            newSocket.trafficClass = TRAFFIC_CLASS_EXPEDITED
            newSocket.bind(InetSocketAddress(port))
        } catch (error: SocketException) {
            newSocket.close()
            throw error
        }
        socket = newSocket
        receiveThread = Thread({ receiveLoop(newSocket) }, "dsu-receive").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        val current = socket ?: return
        socket = null
        current.close()
        receiveThread?.join(STOP_TIMEOUT_MS)
        receiveThread = null
        synchronized(lock) { subscribers.clear() }
    }

    /** Sends the current input of [slot] to every client subscribed to it. */
    fun publish(slot: Int) {
        val current = socket ?: return
        val now = nanoTime()
        synchronized(lock) {
            var packet: ByteArray? = null
            val iterator = subscribers.entries.iterator()
            while (iterator.hasNext()) {
                val (endpoint, subscriber) = iterator.next()
                if (subscriber.isExpired(now) || !access.isApproved(subscriber.address)) {
                    iterator.remove()
                    continue
                }
                if (!subscriber.wants(slot, now)) continue
                val bytes = packet ?: encodePadData(slot)?.also { packet = it } ?: return
                send(current, bytes, endpoint)
            }
        }
    }

    /** Addresses that are receiving pad data right now. */
    fun subscribedAddresses(): Set<String> {
        val now = nanoTime()
        return synchronized(lock) {
            subscribers.values.filterNot { it.isExpired(now) }.mapTo(HashSet()) { it.address }
        }
    }

    private fun encodePadData(slot: Int): ByteArray? {
        val info = pads.slotInfo(slot)
        val frame = pads.frame(slot)
        if (!info.connected || frame == null) return null
        return DsuCodec.encodePadData(serverId, info, ++packetCounters[slot], frame)
    }

    private fun receiveLoop(current: DatagramSocket) {
        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
        val datagram = DatagramPacket(buffer, buffer.size)
        while (true) {
            try {
                datagram.length = buffer.size
                current.receive(datagram)
            } catch (error: IOException) {
                // A close from stop() ends the loop quietly; anything else is a real failure.
                if (socket === current) {
                    socket = null
                    current.close()
                    onFailure(error)
                }
                return
            }
            handle(current, datagram)
        }
    }

    private fun handle(current: DatagramSocket, datagram: DatagramPacket) {
        val address = (datagram.address as? Inet4Address)?.hostAddress ?: return
        val request = DsuCodec.decodeRequest(datagram.data, datagram.length)
        if (!access.isApproved(address)) {
            // Never answered. Only a well-formed request earns a place on the pending list.
            if (request != null) access.offerPending(address)
            return
        }
        val endpoint = InetSocketAddress(datagram.address, datagram.port)
        when (request) {
            null -> Unit
            DsuRequest.Version -> send(current, DsuCodec.encodeVersion(serverId), endpoint)
            is DsuRequest.PortInfo -> for (slot in request.slots) {
                send(current, DsuCodec.encodePortInfo(serverId, pads.slotInfo(slot)), endpoint)
            }
            is DsuRequest.PadData -> subscribe(endpoint, address, request.filter)
        }
    }

    private fun subscribe(endpoint: InetSocketAddress, address: String, filter: PadFilter) {
        val now = nanoTime()
        val until = now + SUBSCRIPTION_SECONDS * NANOS_PER_SECOND
        synchronized(lock) {
            val subscriber = subscribers[endpoint] ?: run {
                subscribers.values.removeAll { it.isExpired(now) }
                if (subscribers.size >= MAX_SUBSCRIBERS) return
                Subscriber(address, now).also { subscribers[endpoint] = it }
            }
            when (filter) {
                PadFilter.AllPads -> subscriber.expiry.fill(until)
                is PadFilter.BySlot -> subscriber.expiry[filter.slot] = until
                is PadFilter.ByMac -> (0 until DsuProtocol.SLOT_COUNT)
                    .firstOrNull { pads.slotInfo(it).run { connected && mac == filter.mac } }
                    ?.let { subscriber.expiry[it] = until }
            }
        }
    }

    private fun send(current: DatagramSocket, bytes: ByteArray, endpoint: InetSocketAddress) {
        try {
            current.send(DatagramPacket(bytes, bytes.size, endpoint))
        } catch (_: IOException) {
            // UDP input is a stream of replaceable samples: a datagram the network refuses is
            // dropped and counted, and the next sample follows a few milliseconds later.
            failedSends.incrementAndGet()
        }
    }

    companion object {
        const val SUBSCRIPTION_SECONDS = 5L
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MAX_SUBSCRIBERS = 16

        /** DSCP 46 (expedited forwarding) in the upper six bits of the IP TOS byte. */
        private const val TRAFFIC_CLASS_EXPEDITED = 0xB8
        private const val STOP_TIMEOUT_MS = 1_000L

        /** Larger than any request, so an oversized datagram is seen whole and can be rejected. */
        private const val RECEIVE_BUFFER_SIZE = 128
    }
}
