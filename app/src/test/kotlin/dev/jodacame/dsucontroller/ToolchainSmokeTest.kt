// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller

import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves that JVM unit tests run in this build and that the platform CRC-32 the DSU codec will
 * rely on is the standard IEEE 802.3 one (check value for "123456789").
 */
class ToolchainSmokeTest {
    @Test
    fun platformCrc32MatchesTheStandardCheckValue() {
        val crc = CRC32().apply { update("123456789".toByteArray(Charsets.US_ASCII)) }

        assertEquals(0xCBF43926L, crc.value)
    }
}
