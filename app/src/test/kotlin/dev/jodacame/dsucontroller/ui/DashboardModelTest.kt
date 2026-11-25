// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import dev.jodacame.dsucontroller.controller.JoyConSide
import dev.jodacame.dsucontroller.controller.SlotView
import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.service.BridgeState
import dev.jodacame.dsucontroller.service.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelTest {
    private val active = SlotView(JoyConSide.LEFT, DsuBattery.HIGH, charging = false, motionArriving = true, buttonPressed = false)

    @Test
    fun aFreeSlotHasNoSideNoBatteryAndKeepsItsLabel() {
        val model = DashboardModel.slot(2, null, streaming = true)

        assertEquals(SlotLook.FREE, model.look)
        assertEquals("P3", model.label)
        assertNull(model.side)
        assertNull(model.battery)
    }

    @Test
    fun aJoyConWithoutMotionGetsTheNoMotionLook() {
        assertEquals(SlotLook.MOTION, DashboardModel.slot(0, active, streaming = true).look)
        assertEquals(SlotLook.NO_MOTION, DashboardModel.slot(0, active.copy(motionArriving = false), streaming = true).look)
    }

    @Test
    fun aJoyConIsShownReadyWhileTheBridgeIsStopped() {
        assertEquals(SlotLook.READY, DashboardModel.slot(0, active.copy(motionArriving = false), streaming = false).look)
    }

    @Test
    fun aHeldButtonInvertsTheBlock() {
        assertTrue(DashboardModel.slot(0, active.copy(buttonPressed = true), streaming = true).inverted)
    }

    @Test
    fun batteryLevelsBecomeSegmentsAndLowIsMarked() {
        assertEquals(BatteryIcon(4, low = false, charging = false), DashboardModel.battery(DsuBattery.FULL, false))
        assertEquals(BatteryIcon(3, low = false, charging = true), DashboardModel.battery(DsuBattery.HIGH, true))
        assertEquals(BatteryIcon(2, low = false, charging = false), DashboardModel.battery(DsuBattery.MEDIUM, false))
        assertEquals(BatteryIcon(1, low = true, charging = false), DashboardModel.battery(DsuBattery.DYING, false))
        assertNull(DashboardModel.battery(DsuBattery.NONE, false))
    }

    @Test
    fun theStatusFollowsTheServerAndItsSubscribers() {
        val base = BridgeState(ServerStatus.Running, 26760, "192.0.2.10", List(4) { null }, 0, emptySet(), null, emptyList())

        assertEquals(ServerText.WAITING, DashboardModel.status(base).serverText)
        val sending = DashboardModel.status(base.copy(subscribers = setOf("192.0.2.20", "192.0.2.21")))
        assertEquals(ServerText.SENDING, sending.serverText)
        assertEquals(2, sending.emulatorCount)
        assertEquals(ServerText.STOPPED, DashboardModel.status(base.copy(status = ServerStatus.Stopped)).serverText)
        assertFalse(DashboardModel.status(base.copy(status = ServerStatus.Stopped)).running)
        val inUse = DashboardModel.status(base.copy(status = ServerStatus.PortInUse(30000)))
        assertEquals(ServerText.PORT_IN_USE, inUse.serverText)
        assertEquals(30000, inUse.port)
        assertTrue(DashboardModel.status(base.copy(waitingJoyCons = 1)).slotsFull)
    }
}
