// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Golden packets. The hex strings were produced by an independent encoder (Python `struct` and
 * `zlib.crc32`, the same code as tools/dsu_client.py), not by this codec.
 */
class DsuCodecTest {
    private val serverId = 0xA1B2C3D4.toInt()
    private val mac = 0x0A1B2C3D4E5FL
    private val slotOne = SlotInfo(slot = 1, connected = true, mac = mac, battery = DsuBattery.HIGH)

    @Test
    fun encodesTheVersionResponse() {
        assertEquals("44535553e90306001c05ceacd4c3b2a100001000e903", DsuCodec.encodeVersion(serverId).hex())
    }

    @Test
    fun encodesPortInfoOfAConnectedSlot() {
        assertEquals(
            "44535553e9031000740bd940d4c3b2a101001000010202020a1b2c3d4e5f0400",
            DsuCodec.encodePortInfo(serverId, slotOne).hex(),
        )
    }

    @Test
    fun encodesPortInfoOfAnEmptySlot() {
        assertEquals(
            "44535553e9031000903a0c7bd4c3b2a101001000030000000000000000000000",
            DsuCodec.encodePortInfo(serverId, SlotInfo.empty(3)).hex(),
        )
    }

    @Test
    fun encodesPadData() {
        val frame = PadFrame(
            buttons = DsuButton.CIRCLE or DsuButton.LEFT or DsuButton.L1 or DsuButton.R2,
            home = true,
            leftStickX = 10,
            leftStickY = 200,
            timestampMicros = 1_234_567_890_123,
            accelX = 0.25f,
            accelY = -1.0f,
            accelZ = 0.5f,
            gyroPitch = 90.0f,
            gyroYaw = -45.5f,
            gyroRoll = 12.25f,
        )

        val packet = DsuCodec.encodePadData(
            serverId,
            slotOne.copy(battery = DsuBattery.CHARGING),
            packetCounter = 0x01020304,
            frame = frame,
        )

        assertEquals(
            "44535553e90354006ec42d4dd4c3b2a102001000010202020a1b2c3d4e5fee0104030201802601000ac87f7f" +
                "ff0000000000ff0000ffff00000000000000000000000000cb04fb711f0100000000803e000080bf0000003f" +
                "0000b442000036c200004441",
            packet.hex(),
        )
    }

    @Test
    fun decodesAVersionRequest() {
        assertEquals(DsuRequest.Version, decode("44535543e9030400ef02b82e4433221100001000"))
    }

    @Test
    fun decodesAPortInfoRequestForAllSlots() {
        assertEquals(
            DsuRequest.PortInfo(listOf(0, 1, 2, 3)),
            decode("44535543e9030c006ac4742c44332211010010000400000000010203"),
        )
    }

    @Test
    fun decodesPadDataRequestsForAllPadsOneSlotAndOneMac() {
        assertEquals(
            DsuRequest.PadData(PadFilter.AllPads),
            decode("44535543e9030c006f415d7544332211020010000000000000000000"),
        )
        assertEquals(
            DsuRequest.PadData(PadFilter.BySlot(2)),
            decode("44535543e9030c00d850682e44332211020010000102000000000000"),
        )
        assertEquals(
            DsuRequest.PadData(PadFilter.ByMac(mac)),
            decode("44535543e9030c004c8baad2443322110200100002000a1b2c3d4e5f"),
        )
    }

    @Test
    fun acceptsARequestFollowedByExtraBytes() {
        assertEquals(DsuRequest.Version, decode("44535543e9030400ef02b82e4433221100001000" + "ffff"))
    }

    @Test
    fun rejectsATruncatedPacket() {
        assertNull(decode("44535543e9030400ef02b82e44332211000010"))
    }

    @Test
    fun rejectsTheServerMagic() {
        assertNull(decode(resealed("44535553e9030400000000004433221100001000")))
    }

    @Test
    fun rejectsAnotherProtocolVersion() {
        assertNull(decode(resealed("44535543ea030400000000004433221100001000")))
    }

    @Test
    fun rejectsADeclaredLengthLongerThanTheDatagram() {
        assertNull(decode(resealed("44535543e9030c00000000004433221100001000")))
    }

    @Test
    fun rejectsABadCrc() {
        assertNull(decode("44535543e9030400ef02b82f4433221100001000"))
    }

    @Test
    fun rejectsAnUnknownMessageType() {
        assertNull(decode(resealed("44535543e9030400000000004433221103001000")))
    }

    @Test
    fun rejectsAPadDataRequestForASlotOutOfRange() {
        assertNull(decode(resealed("44535543e9030c000000000044332211020010000104000000000000")))
    }

    @Test
    fun rejectsAnUnknownPadDataFilter() {
        assertNull(decode(resealed("44535543e9030c000000000044332211020010000900000000000000")))
    }

    @Test
    fun ignoresSlotsOutOfRangeAndClampsThePadCount() {
        // Pad count 9 with slots 0, 7, 1, 1: only the in-range, distinct slots of the first four.
        val request = decode(resealed("44535543e9030c000000000044332211010010000900000000070101"))

        assertEquals(DsuRequest.PortInfo(listOf(0, 1)), request)
    }

    private fun decode(hex: String): DsuRequest? {
        val bytes = hex.unhex()
        return DsuCodec.decodeRequest(bytes, bytes.size)
    }

    /** Fills in a valid CRC so a test rejects the packet for the reason it names, not the CRC. */
    private fun resealed(hex: String): String {
        val bytes = hex.unhex()
        for (index in 8..11) bytes[index] = 0
        val crc = java.util.zip.CRC32().apply { update(bytes) }.value
        for (index in 0..3) bytes[8 + index] = (crc ushr (8 * index)).toByte()
        return bytes.hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
