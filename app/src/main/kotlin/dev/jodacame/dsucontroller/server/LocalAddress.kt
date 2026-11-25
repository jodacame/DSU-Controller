// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** Finds the address an emulator on the local network should connect to. */
object LocalAddress {
    private const val WIFI_INTERFACE_PREFIX = "wlan"

    /**
     * Returns this device's private IPv4 address, preferring Wi-Fi, or null when it has none.
     * Reads the interface list only; it opens no socket and does no lookup.
     */
    fun find(): String? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: SocketException) {
            // The platform could not list interfaces: the same to the user as having no address.
            return null
        }
        return interfaces
            .filter { isUsable(it) }
            .sortedByDescending { it.name.startsWith(WIFI_INTERFACE_PREFIX) }
            .firstNotNullOfOrNull { network ->
                network.inetAddresses.toList()
                    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                    ?.hostAddress
            }
    }

    private fun isUsable(network: NetworkInterface): Boolean = try {
        network.isUp && !network.isLoopback
    } catch (_: SocketException) {
        false
    }
}
