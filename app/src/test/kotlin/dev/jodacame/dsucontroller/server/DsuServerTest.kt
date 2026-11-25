// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.protocol.PadFrame
import dev.jodacame.dsucontroller.protocol.SlotInfo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Runs the real server on a loopback socket and talks to it as a DSU client would. */
class DsuServerTest {
    private class FakePads : PadSource {
        val connected = mutableSetOf(0)

        override fun slotInfo(slot: Int): SlotInfo =
            if (slot in connected) SlotInfo(slot, true, MAC + slot, DsuBattery.FULL) else SlotInfo.empty(slot)

        override fun frame(slot: Int): PadFrame? = if (slot in connected) PadFrame() else null
    }

    private val loopback = InetAddress.getLoopbackAddress()
    private val clock = AtomicLong(1_000)
    private val pads = FakePads()
    private val access = ClientAccess(listOf(LOOPBACK_ADDRESS))
    private val server = DsuServer(pads, access, nanoTime = clock::get)
    private val client = DatagramSocket(0, loopback)

    @Before
    fun startServer() {
        client.soTimeout = ANSWER_TIMEOUT_MS
        server.start(port = 0)
    }

    @After
    fun stopServer() {
        client.close()
        server.stop()
    }

    @Test
    fun answersAVersionRequest() {
        send(VERSION_REQUEST)

        assertEquals(VERSION_RESPONSE_SIZE, receive().size)
    }

    @Test
    fun answersPortInfoOncePerRequestedSlot() {
        send(PORT_INFO_ALL_SLOTS)

        val answers = List(4) { receive() }

        assertEquals(listOf(0, 1, 2, 3), answers.map { it[SLOT_OFFSET].toInt() })
        assertEquals(listOf(2, 0, 0, 0), answers.map { it[STATE_OFFSET].toInt() })
        assertSilence()
    }

    @Test
    fun neverAnswersAnUnapprovedAddressAndListsItAsPending() {
        access.remove(LOOPBACK_ADDRESS)

        send(VERSION_REQUEST)

        assertSilence()
        assertEquals(listOf(LOOPBACK_ADDRESS), access.pending())
    }

    @Test
    fun garbageFromAnUnapprovedAddressIsNotListedAsPending() {
        access.remove(LOOPBACK_ADDRESS)

        send("not a dsu packet at all".toByteArray())

        assertSilence()
        assertEquals(emptyList<String>(), access.pending())
    }

    @Test
    fun streamsPadDataToASubscriberWithAnIncreasingCounter() {
        subscribe(PAD_DATA_ALL_PADS)

        server.publish(0)
        server.publish(0)
        val first = receive()
        val second = receive()

        assertEquals(PAD_DATA_RESPONSE_SIZE, first.size)
        assertEquals(counterOf(first) + 1, counterOf(second))
        assertEquals(setOf(LOOPBACK_ADDRESS), server.subscribedAddresses())
    }

    @Test
    fun sendsNothingBeforeAnyPadDataRequest() {
        server.publish(0)

        assertSilence()
    }

    @Test
    fun sendsNothingForAnEmptySlot() {
        subscribe(PAD_DATA_ALL_PADS)

        server.publish(1)

        assertSilence()
    }

    @Test
    fun aSubscriptionToOneSlotDoesNotReceiveTheOthers() {
        pads.connected += 2
        subscribe(PAD_DATA_SLOT_TWO)

        server.publish(0)
        server.publish(2)

        assertEquals(2, receive()[SLOT_OFFSET].toInt())
        assertSilence()
    }

    @Test
    fun aSubscriptionByMacReceivesThatController() {
        subscribe(PAD_DATA_BY_MAC)

        server.publish(0)

        assertEquals(PAD_DATA_RESPONSE_SIZE, receive().size)
    }

    @Test
    fun stopsStreamingFiveSecondsAfterTheLastRequest() {
        subscribe(PAD_DATA_ALL_PADS)
        clock.addAndGet(5_000_000_001)

        server.publish(0)

        assertSilence()
        assertEquals(emptySet<String>(), server.subscribedAddresses())
    }

    @Test
    fun stopsStreamingToAClientTheUserRemoved() {
        subscribe(PAD_DATA_ALL_PADS)
        access.remove(LOOPBACK_ADDRESS)

        server.publish(0)

        assertSilence()
    }

    @Test
    fun sendsNothingAfterStop() {
        subscribe(PAD_DATA_ALL_PADS)

        server.stop()
        server.publish(0)

        assertSilence()
    }

    @Test
    fun aRestartForgetsTheOldSubscriptionsAndAnswersAgain() {
        subscribe(PAD_DATA_ALL_PADS)

        server.stop()
        server.start(port = 0)
        server.publish(0)

        assertSilence()
        assertEquals(emptySet<String>(), server.subscribedAddresses())
        subscribe(PAD_DATA_ALL_PADS)
        server.publish(0)
        assertEquals(PAD_DATA_RESPONSE_SIZE, receive().size)
    }

    @Test
    fun refusesAPortThatIsInUse() {
        val second = DsuServer(pads, access)

        assertThrows(java.net.SocketException::class.java) { second.start(server.localPort) }
    }

    /**
     * Sends a pad data request and waits until the server has processed it. Requests are
     * handled in order on one thread, so the answer to a following version request proves it.
     */
    private fun subscribe(request: ByteArray) {
        send(request)
        send(VERSION_REQUEST)
        assertEquals(VERSION_RESPONSE_SIZE, receive().size)
    }

    private fun send(bytes: ByteArray) {
        client.send(DatagramPacket(bytes, bytes.size, loopback, server.localPort))
    }

    private fun receive(): ByteArray {
        val datagram = DatagramPacket(ByteArray(256), 256)
        client.receive(datagram)
        return datagram.data.copyOf(datagram.length)
    }

    private fun assertSilence() {
        client.soTimeout = SILENCE_TIMEOUT_MS
        assertThrows(SocketTimeoutException::class.java) { receive() }
        client.soTimeout = ANSWER_TIMEOUT_MS
    }

    private fun counterOf(packet: ByteArray): Int =
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(COUNTER_OFFSET)

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val MAC = 0x0A1B2C3D4E5FL
        const val ANSWER_TIMEOUT_MS = 2_000
        const val SILENCE_TIMEOUT_MS = 150
        const val VERSION_RESPONSE_SIZE = 22
        const val PAD_DATA_RESPONSE_SIZE = 100
        const val SLOT_OFFSET = 20
        const val STATE_OFFSET = 21
        const val COUNTER_OFFSET = 32

        // Requests as tools/dsu_client.py builds them (client id 0x11223344).
        val VERSION_REQUEST = unhex("44535543e9030400ef02b82e4433221100001000")
        val PORT_INFO_ALL_SLOTS = unhex("44535543e9030c006ac4742c44332211010010000400000000010203")
        val PAD_DATA_ALL_PADS = unhex("44535543e9030c006f415d7544332211020010000000000000000000")
        val PAD_DATA_SLOT_TWO = unhex("44535543e9030c00d850682e44332211020010000102000000000000")
        val PAD_DATA_BY_MAC = unhex("44535543e9030c004c8baad2443322110200100002000a1b2c3d4e5f")

        fun unhex(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
