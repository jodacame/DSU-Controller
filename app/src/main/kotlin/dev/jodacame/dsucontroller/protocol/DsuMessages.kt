// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.protocol

/** Constants of the DSU (cemuhook) protocol. The wire format is in docs/contracts/dsu-protocol.md. */
object DsuProtocol {
    const val DEFAULT_PORT = 26760
    const val SLOT_COUNT = 4
    const val PROTOCOL_VERSION = 1001

    /** Value of a stick byte at rest; the client computes `(value - 127) / 127`. */
    const val STICK_NEUTRAL = 127
    const val STICK_MAX = 254
}

/** Bit masks of the 16-bit digital button field of a pad data packet. */
object DsuButton {
    const val SHARE = 1 shl 0
    const val L3 = 1 shl 1
    const val R3 = 1 shl 2
    const val OPTIONS = 1 shl 3
    const val UP = 1 shl 4
    const val RIGHT = 1 shl 5
    const val DOWN = 1 shl 6
    const val LEFT = 1 shl 7
    const val L2 = 1 shl 8
    const val R2 = 1 shl 9
    const val L1 = 1 shl 10
    const val R1 = 1 shl 11
    const val TRIANGLE = 1 shl 12
    const val CIRCLE = 1 shl 13
    const val CROSS = 1 shl 14
    const val SQUARE = 1 shl 15
}

/** Battery level as the protocol encodes it. */
enum class DsuBattery(val wireValue: Int) {
    NONE(0x00),
    DYING(0x01),
    LOW(0x02),
    MEDIUM(0x03),
    HIGH(0x04),
    FULL(0x05),
    CHARGING(0xEE),
    CHARGED(0xEF),
}

/**
 * Identity and state of one controller slot, as sent in port info and pad data responses.
 *
 * @property mac a stable 48-bit id of the controller (not its Bluetooth address); zero when empty
 */
data class SlotInfo(
    val slot: Int,
    val connected: Boolean,
    val mac: Long,
    val battery: DsuBattery,
) {
    companion object {
        fun empty(slot: Int) = SlotInfo(slot, connected = false, mac = 0L, battery = DsuBattery.NONE)
    }
}

/**
 * The input of one controller at one moment, already in wire units.
 *
 * @property buttons bit field of [DsuButton] masks
 * @property timestampMicros monotonic time of the motion sample
 * @property accelX accelerometer in g
 * @property gyroPitch gyroscope in degrees per second
 */
data class PadFrame(
    val buttons: Int = 0,
    val home: Boolean = false,
    val touchHardPress: Boolean = false,
    val leftStickX: Int = DsuProtocol.STICK_NEUTRAL,
    val leftStickY: Int = DsuProtocol.STICK_NEUTRAL,
    val rightStickX: Int = DsuProtocol.STICK_NEUTRAL,
    val rightStickY: Int = DsuProtocol.STICK_NEUTRAL,
    val timestampMicros: Long = 0L,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroPitch: Float = 0f,
    val gyroYaw: Float = 0f,
    val gyroRoll: Float = 0f,
)

/** Which controllers a pad data request subscribes to. */
sealed interface PadFilter {
    data object AllPads : PadFilter

    data class BySlot(val slot: Int) : PadFilter

    data class ByMac(val mac: Long) : PadFilter
}

/** A valid request from a DSU client. */
sealed interface DsuRequest {
    data object Version : DsuRequest

    /** @property slots the requested slots, each in 0..3, without duplicates */
    data class PortInfo(val slots: List<Int>) : DsuRequest

    data class PadData(val filter: PadFilter) : DsuRequest
}
