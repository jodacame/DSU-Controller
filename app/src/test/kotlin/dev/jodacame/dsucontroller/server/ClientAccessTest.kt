// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientAccessTest {
    @Test
    fun nothingIsApprovedByDefault() {
        assertFalse(ClientAccess().isApproved("192.0.2.10"))
    }

    @Test
    fun approvingAPendingAddressRemovesItFromThePendingList() {
        val access = ClientAccess()
        access.offerPending("192.0.2.10")

        assertTrue(access.approve("192.0.2.10"))

        assertTrue(access.isApproved("192.0.2.10"))
        assertEquals(emptyList<String>(), access.pending())
    }

    @Test
    fun approvingTwiceReportsTheDuplicate() {
        val access = ClientAccess(listOf("192.0.2.10"))

        assertFalse(access.approve("192.0.2.10"))
    }

    @Test
    fun anIgnoredAddressDoesNotComeBack() {
        val access = ClientAccess()
        access.offerPending("192.0.2.10")

        access.ignore("192.0.2.10")
        access.offerPending("192.0.2.10")

        assertEquals(emptyList<String>(), access.pending())
        assertFalse(access.isApproved("192.0.2.10"))
    }

    @Test
    fun thePendingListIsBoundedAndHasNoDuplicates() {
        val access = ClientAccess()

        repeat(2) { for (host in 1..20) access.offerPending("192.0.2.$host") }

        assertEquals((1..ClientAccess.MAX_PENDING).map { "192.0.2.$it" }, access.pending())
    }

    @Test
    fun aRemovedAddressIsNoLongerApproved() {
        val access = ClientAccess(listOf("192.0.2.10"))

        access.remove("192.0.2.10")

        assertFalse(access.isApproved("192.0.2.10"))
    }
}
