// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import dev.jodacame.dsucontroller.controller.JoyConSide
import dev.jodacame.dsucontroller.controller.SlotView
import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.service.BridgeState
import dev.jodacame.dsucontroller.service.ServerStatus

/** How a slot block looks (docs/contracts/dashboard-ui.md, "Slot states"). */
enum class SlotLook { FREE, READY, MOTION, NO_MOTION }

/**
 * The battery icon: [segments] filled out of four, or no icon at all when null.
 * [low] draws the single segment inside a filled pill, so low differs by shape too.
 */
data class BatteryIcon(val segments: Int, val low: Boolean, val charging: Boolean)

/** Everything one slot block draws, without any Android type. */
data class SlotModel(
    val slot: Int,
    val look: SlotLook,
    val side: JoyConSide?,
    val battery: BatteryIcon?,
    val inverted: Boolean,
) {
    val label: String get() = "P${slot + 1}"
}

/** What the status bar says about the server. */
enum class ServerText { STOPPED, WAITING, SENDING, PORT_IN_USE, NETWORK_ERROR }

data class StatusModel(
    val serverText: ServerText,
    val emulatorCount: Int,
    val running: Boolean,
    val address: String?,
    val port: Int,
    val slotsFull: Boolean,
)

/** Turns the bridge state into what the dashboard draws. Pure, so it is unit-tested. */
object DashboardModel {
    private const val FULL_SEGMENTS = 4

    /** @param streaming false while the bridge is stopped: motion is not read then */
    fun slot(slot: Int, view: SlotView?, streaming: Boolean): SlotModel {
        if (view == null) return SlotModel(slot, SlotLook.FREE, side = null, battery = null, inverted = false)
        val look = when {
            !streaming -> SlotLook.READY
            view.motionArriving -> SlotLook.MOTION
            else -> SlotLook.NO_MOTION
        }
        return SlotModel(
            slot = slot,
            look = look,
            side = view.side,
            battery = battery(view.batteryLevel, view.charging),
            inverted = view.buttonPressed,
        )
    }

    fun battery(level: DsuBattery, charging: Boolean): BatteryIcon? {
        val segments = when (level) {
            DsuBattery.FULL, DsuBattery.CHARGED -> FULL_SEGMENTS
            DsuBattery.HIGH -> 3
            DsuBattery.MEDIUM -> 2
            DsuBattery.LOW, DsuBattery.DYING -> 1
            DsuBattery.NONE, DsuBattery.CHARGING -> return null
        }
        return BatteryIcon(segments, low = segments == 1, charging = charging)
    }

    fun status(state: BridgeState): StatusModel {
        val text = when (val status = state.status) {
            ServerStatus.Stopped -> ServerText.STOPPED
            ServerStatus.Running -> if (state.subscribers.isEmpty()) ServerText.WAITING else ServerText.SENDING
            is ServerStatus.PortInUse -> ServerText.PORT_IN_USE
            ServerStatus.NetworkError -> ServerText.NETWORK_ERROR
        }
        return StatusModel(
            serverText = text,
            emulatorCount = state.subscribers.size,
            running = state.status == ServerStatus.Running,
            address = state.localAddress,
            port = (state.status as? ServerStatus.PortInUse)?.port ?: state.port,
            slotsFull = state.waitingJoyCons > 0,
        )
    }
}
