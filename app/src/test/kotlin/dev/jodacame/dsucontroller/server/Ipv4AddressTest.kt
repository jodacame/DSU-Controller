// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4AddressTest {
    @Test
    fun acceptsADottedAddressAndTrimsIt() {
        assertEquals("192.0.2.10", Ipv4Address.normalize("  192.0.2.10 "))
    }

    @Test
    fun removesLeadingZeros() {
        assertEquals("192.0.2.7", Ipv4Address.normalize("192.000.002.007"))
    }

    @Test
    fun rejectsEverythingElse() {
        val invalid = listOf(
            "", "192.0.2", "192.0.2.10.1", "192.0.2.256", "192.0.2.-1", "192.0.2.x",
            "192.0..10", "example.org", "192.0.2.1000", "192.0.2.10:26760", "::1",
        )

        for (text in invalid) assertNull(text, Ipv4Address.normalize(text))
    }
}
