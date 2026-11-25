// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

/**
 * Which half of a Joy-Con pair an input device is, identified by its USB/Bluetooth ids.
 *
 * @property productId the product id the controller reports
 * @property label the name shown to the user
 */
enum class JoyConSide(val productId: Int, val label: String) {
    LEFT(0x2006, "Left Joy-Con"),
    RIGHT(0x2007, "Right Joy-Con");

    companion object {
        const val NINTENDO_VENDOR_ID = 0x057E

        /** Returns the side for these ids, or null when the device is not a Joy-Con. */
        fun fromIds(vendorId: Int, productId: Int): JoyConSide? {
            if (vendorId != NINTENDO_VENDOR_ID) return null
            return entries.firstOrNull { it.productId == productId }
        }
    }
}
