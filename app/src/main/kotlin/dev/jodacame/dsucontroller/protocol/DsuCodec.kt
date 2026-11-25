// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Byte-exact encoder and decoder of the DSU wire format (docs/contracts/dsu-protocol.md).
 * Stateless and safe to call from any thread.
 */
object DsuCodec {
    /** Largest request a client sends; a receive buffer of this size is enough. */
    const val MAX_REQUEST_SIZE = 28

    private const val MAGIC_CLIENT = 0x43555344 // "DSUC" read as a little-endian int
    private const val MAGIC_SERVER = 0x53555344 // "DSUS"
    private const val TYPE_VERSION = 0x100000
    private const val TYPE_PORT_INFO = 0x100001
    private const val TYPE_PAD_DATA = 0x100002

    private const val HEADER_SIZE = 20
    private const val LENGTH_OFFSET = 6
    private const val CRC_OFFSET = 8
    private const val ID_OFFSET = 12
    private const val TYPE_OFFSET = 16

    /** The length field counts everything after the id, so the type field is part of it. */
    private const val BYTES_BEFORE_PAYLOAD = 16
    private const val BODY_OFFSET = HEADER_SIZE
    private const val REQUEST_WITH_BODY_SIZE = 28

    private const val VERSION_RESPONSE_SIZE = 22
    private const val PORT_INFO_RESPONSE_SIZE = 32
    private const val PAD_DATA_RESPONSE_SIZE = 100

    private const val SLOT_STATE_CONNECTED = 2
    private const val MODEL_FULL_GYRO = 2
    private const val CONNECTION_BLUETOOTH = 2
    private const val MAC_SIZE = 6

    private const val PAD_ACTIVE_OFFSET = 31
    private const val PAD_COUNTER_OFFSET = 32
    private const val PAD_BUTTONS_OFFSET = 36
    private const val PAD_HOME_OFFSET = 38
    private const val PAD_TOUCH_PRESS_OFFSET = 39
    private const val PAD_STICKS_OFFSET = 40
    private const val PAD_ANALOG_OFFSET = 44
    private const val PAD_TIMESTAMP_OFFSET = 68
    private const val PAD_ACCEL_OFFSET = 76
    private const val PAD_GYRO_OFFSET = 88
    private const val ANALOG_PRESSED = 0xFF

    private const val FLAGS_ALL_PADS = 0
    private const val FLAGS_BY_SLOT = 1
    private const val FLAGS_BY_MAC = 2

    /** Analog button bytes in wire order; each mirrors one digital button. */
    private val ANALOG_ORDER = intArrayOf(
        DsuButton.LEFT, DsuButton.DOWN, DsuButton.RIGHT, DsuButton.UP,
        DsuButton.SQUARE, DsuButton.CROSS, DsuButton.CIRCLE, DsuButton.TRIANGLE,
        DsuButton.R1, DsuButton.L1, DsuButton.R2, DsuButton.L2,
    )
    private val ZERO_CRC = ByteArray(Int.SIZE_BYTES)

    /**
     * Decodes the first [length] bytes of [data] as a client request.
     *
     * Returns null for anything that is not a well-formed request: too short, wrong magic or
     * protocol version, a declared length that does not fit the datagram, a bad CRC, an unknown
     * message type or an out-of-range slot.
     */
    fun decodeRequest(data: ByteArray, length: Int): DsuRequest? {
        if (length < HEADER_SIZE || length > data.size) return null
        val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != MAGIC_CLIENT) return null
        if (buffer.getShort(4).toInt() and 0xFFFF != DsuProtocol.PROTOCOL_VERSION) return null
        val packetLength = BYTES_BEFORE_PAYLOAD + (buffer.getShort(LENGTH_OFFSET).toInt() and 0xFFFF)
        if (packetLength < HEADER_SIZE || packetLength > length) return null
        if (crcOf(data, packetLength) != buffer.getInt(CRC_OFFSET)) return null

        return when (buffer.getInt(TYPE_OFFSET)) {
            TYPE_VERSION -> DsuRequest.Version
            TYPE_PORT_INFO -> if (packetLength < REQUEST_WITH_BODY_SIZE) null else decodePortInfo(buffer)
            TYPE_PAD_DATA -> if (packetLength < REQUEST_WITH_BODY_SIZE) null else decodePadData(buffer)
            else -> null
        }
    }

    fun encodeVersion(serverId: Int): ByteArray {
        val buffer = newPacket(VERSION_RESPONSE_SIZE, serverId, TYPE_VERSION)
        buffer.putShort(BODY_OFFSET, DsuProtocol.PROTOCOL_VERSION.toShort())
        return seal(buffer)
    }

    fun encodePortInfo(serverId: Int, slot: SlotInfo): ByteArray {
        val buffer = newPacket(PORT_INFO_RESPONSE_SIZE, serverId, TYPE_PORT_INFO)
        putSlotBlock(buffer, slot)
        return seal(buffer)
    }

    /** @param packetCounter must differ from the previous packet of the same slot */
    fun encodePadData(serverId: Int, slot: SlotInfo, packetCounter: Int, frame: PadFrame): ByteArray {
        val buffer = newPacket(PAD_DATA_RESPONSE_SIZE, serverId, TYPE_PAD_DATA)
        putSlotBlock(buffer, slot)
        buffer.put(PAD_ACTIVE_OFFSET, flag(slot.connected))
        buffer.putInt(PAD_COUNTER_OFFSET, packetCounter)
        buffer.putShort(PAD_BUTTONS_OFFSET, frame.buttons.toShort())
        buffer.put(PAD_HOME_OFFSET, flag(frame.home))
        buffer.put(PAD_TOUCH_PRESS_OFFSET, flag(frame.touchHardPress))
        buffer.put(PAD_STICKS_OFFSET, frame.leftStickX.toByte())
        buffer.put(PAD_STICKS_OFFSET + 1, frame.leftStickY.toByte())
        buffer.put(PAD_STICKS_OFFSET + 2, frame.rightStickX.toByte())
        buffer.put(PAD_STICKS_OFFSET + 3, frame.rightStickY.toByte())
        for ((index, mask) in ANALOG_ORDER.withIndex()) {
            if (frame.buttons and mask != 0) buffer.put(PAD_ANALOG_OFFSET + index, ANALOG_PRESSED.toByte())
        }
        // Bytes 56..67 are the two touch points; they stay zero (inactive).
        buffer.putLong(PAD_TIMESTAMP_OFFSET, frame.timestampMicros)
        buffer.putFloat(PAD_ACCEL_OFFSET, frame.accelX)
        buffer.putFloat(PAD_ACCEL_OFFSET + 4, frame.accelY)
        buffer.putFloat(PAD_ACCEL_OFFSET + 8, frame.accelZ)
        buffer.putFloat(PAD_GYRO_OFFSET, frame.gyroPitch)
        buffer.putFloat(PAD_GYRO_OFFSET + 4, frame.gyroYaw)
        buffer.putFloat(PAD_GYRO_OFFSET + 8, frame.gyroRoll)
        return seal(buffer)
    }

    private fun decodePortInfo(buffer: ByteBuffer): DsuRequest {
        val requested = buffer.getInt(BODY_OFFSET).coerceIn(0, DsuProtocol.SLOT_COUNT)
        val slots = (0 until requested)
            .map { buffer.get(BODY_OFFSET + Int.SIZE_BYTES + it).toInt() and 0xFF }
            .filter { it < DsuProtocol.SLOT_COUNT }
            .distinct()
        return DsuRequest.PortInfo(slots)
    }

    private fun decodePadData(buffer: ByteBuffer): DsuRequest? {
        val slot = buffer.get(BODY_OFFSET + 1).toInt() and 0xFF
        return when (buffer.get(BODY_OFFSET).toInt() and 0xFF) {
            FLAGS_ALL_PADS -> DsuRequest.PadData(PadFilter.AllPads)
            FLAGS_BY_SLOT ->
                if (slot < DsuProtocol.SLOT_COUNT) DsuRequest.PadData(PadFilter.BySlot(slot)) else null
            FLAGS_BY_MAC -> DsuRequest.PadData(PadFilter.ByMac(getMac(buffer, BODY_OFFSET + 2)))
            else -> null
        }
    }

    private fun newPacket(size: Int, serverId: Int, type: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, MAGIC_SERVER)
            putShort(4, DsuProtocol.PROTOCOL_VERSION.toShort())
            putShort(LENGTH_OFFSET, (size - BYTES_BEFORE_PAYLOAD).toShort())
            putInt(ID_OFFSET, serverId)
            putInt(TYPE_OFFSET, type)
        }

    private fun putSlotBlock(buffer: ByteBuffer, slot: SlotInfo) {
        buffer.put(BODY_OFFSET, slot.slot.toByte())
        if (!slot.connected) return
        buffer.put(BODY_OFFSET + 1, SLOT_STATE_CONNECTED.toByte())
        buffer.put(BODY_OFFSET + 2, MODEL_FULL_GYRO.toByte())
        buffer.put(BODY_OFFSET + 3, CONNECTION_BLUETOOTH.toByte())
        for (index in 0 until MAC_SIZE) {
            val shift = Byte.SIZE_BITS * (MAC_SIZE - 1 - index)
            buffer.put(BODY_OFFSET + 4 + index, (slot.mac ushr shift).toByte())
        }
        buffer.put(BODY_OFFSET + 4 + MAC_SIZE, slot.battery.wireValue.toByte())
    }

    private fun getMac(buffer: ByteBuffer, offset: Int): Long {
        var mac = 0L
        for (index in 0 until MAC_SIZE) {
            mac = (mac shl Byte.SIZE_BITS) or (buffer.get(offset + index).toLong() and 0xFF)
        }
        return mac
    }

    private fun seal(buffer: ByteBuffer): ByteArray {
        val packet = buffer.array()
        buffer.putInt(CRC_OFFSET, crcOf(packet, packet.size))
        return packet
    }

    /** CRC-32 of the packet as if its CRC field were zero. */
    private fun crcOf(packet: ByteArray, length: Int): Int {
        val crc = CRC32()
        crc.update(packet, 0, CRC_OFFSET)
        crc.update(ZERO_CRC)
        crc.update(packet, ID_OFFSET, length - ID_OFFSET)
        return crc.value.toInt()
    }

    private fun flag(value: Boolean): Byte = if (value) 1 else 0
}
