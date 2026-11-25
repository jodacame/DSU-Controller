// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

/** Parsing of dotted IPv4 addresses without any name lookup. */
object Ipv4Address {
    private const val PARTS = 4
    private const val MAX_PART_DIGITS = 3
    private const val MAX_PART_VALUE = 255

    /**
     * Returns [text] as a canonical dotted address (`192.0.2.10`), or null when it is not four
     * decimal numbers from 0 to 255. Host names are never accepted.
     */
    fun normalize(text: String): String? {
        val parts = text.trim().split('.')
        if (parts.size != PARTS) return null
        val numbers = parts.map { part ->
            if (part.isEmpty() || part.length > MAX_PART_DIGITS || !part.all { it in '0'..'9' }) return null
            part.toInt().also { if (it > MAX_PART_VALUE) return null }
        }
        return numbers.joinToString(".")
    }
}
