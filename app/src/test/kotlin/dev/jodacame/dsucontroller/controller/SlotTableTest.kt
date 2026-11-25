// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotTableTest {
    private val table = SlotTable(slotCount = 4)

    @Test
    fun assignsTheLowestFreeSlot() {
        assertEquals(0, table.assign("a"))
        assertEquals(1, table.assign("b"))
    }

    @Test
    fun aControllerKeepsItsSlot() {
        table.assign("a")

        assertEquals(0, table.assign("a"))
    }

    @Test
    fun aReconnectingControllerReturnsToItsPreviousSlot() {
        table.assign("a")
        table.assign("b")
        table.release("a")
        table.release("b")

        assertEquals(1, table.assign("b"))
    }

    @Test
    fun aTakenPreviousSlotFallsBackToTheLowestFree() {
        table.assign("a")
        table.assign("b")
        table.release("b")
        table.assign("c")

        assertEquals(2, table.assign("b"))
    }

    @Test
    fun aFifthControllerGetsNoSlot() {
        for (id in listOf("a", "b", "c", "d")) table.assign(id)

        assertNull(table.assign("e"))
    }

    @Test
    fun theStableMacIsRepeatableDistinctAndFitsIn48Bits() {
        val first = SlotTable.stableMac("descriptor-one")

        assertEquals(first, SlotTable.stableMac("descriptor-one"))
        assertNotEquals(first, SlotTable.stableMac("descriptor-two"))
        assertEquals(0L, first ushr 48)
    }
}
