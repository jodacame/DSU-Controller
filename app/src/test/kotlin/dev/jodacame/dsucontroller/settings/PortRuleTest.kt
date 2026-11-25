// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortRuleTest {
    @Test
    fun acceptsPortsInTheUnprivilegedRange() {
        assertEquals(26760, PortRule.parse(" 26760 "))
        assertEquals(1024, PortRule.parse("1024"))
        assertEquals(65535, PortRule.parse("65535"))
    }

    @Test
    fun rejectsEverythingElse() {
        for (text in listOf("", "1023", "65536", "-1", "abc", "26760.5", "99999999999")) {
            assertNull(text, PortRule.parse(text))
        }
    }
}
