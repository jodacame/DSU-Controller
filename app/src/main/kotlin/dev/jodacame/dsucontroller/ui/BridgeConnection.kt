// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.jodacame.dsucontroller.service.Bridge
import dev.jodacame.dsucontroller.service.BridgeService

/** Binds a screen to [BridgeService] while the screen is visible. */
class BridgeConnection(private val context: Context, private val onConnected: () -> Unit) {
    private var binder: BridgeService.LocalBinder? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service as BridgeService.LocalBinder
            onConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
        }
    }

    /** The bridge, or null until the service is connected. */
    val bridge: Bridge? get() = binder?.bridge

    fun bind() {
        if (bound) return
        bound = context.bindService(Intent(context, BridgeService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!bound) return
        context.unbindService(connection)
        bound = false
        binder = null
    }

    fun stopBridge() = binder?.stopBridge()
}
