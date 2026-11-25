// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.service

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import dev.jodacame.dsucontroller.controller.JoyConRegistry
import dev.jodacame.dsucontroller.controller.SlotView
import dev.jodacame.dsucontroller.server.ClientAccess
import dev.jodacame.dsucontroller.server.DsuServer
import dev.jodacame.dsucontroller.server.Ipv4Address
import dev.jodacame.dsucontroller.server.LocalAddress
import dev.jodacame.dsucontroller.settings.BridgeSettings
import dev.jodacame.dsucontroller.settings.PortRule
import java.net.BindException
import java.net.SocketException

/** Whether the DSU server is answering, and if not, why. */
sealed interface ServerStatus {
    data object Stopped : ServerStatus

    data object Running : ServerStatus

    data class PortInUse(val port: Int) : ServerStatus

    /** The socket could not be opened or failed while running. */
    data object NetworkError : ServerStatus
}

/**
 * Everything the screens show, as one immutable value.
 *
 * @property localAddress this phone's address on the local network, null without one
 * @property slots one entry per slot, null where free
 * @property waitingJoyCons Joy-Con connected beyond the four slots
 * @property subscribers addresses receiving controller data right now
 * @property pendingClient the next address waiting for the user's answer
 */
data class BridgeState(
    val status: ServerStatus,
    val port: Int,
    val localAddress: String?,
    val slots: List<SlotView?>,
    val waitingJoyCons: Int,
    val subscribers: Set<String>,
    val pendingClient: String?,
    val approvedClients: List<String>,
)

/** Result of typing an emulator address by hand. */
enum class AddClientResult { ADDED, INVALID, ALREADY_ALLOWED }

/**
 * Joins the Joy-Con registry and the DSU server. Owned by [BridgeService]; every method is
 * called from the main thread.
 */
class Bridge(context: Context, private val settings: BridgeSettings) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bridgeThread = HandlerThread("dsu-bridge").apply { start() }
    private val bridgeHandler = Handler(bridgeThread.looper)
    private val access = ClientAccess(settings.approvedClients)
    private val registry: JoyConRegistry = JoyConRegistry(context, bridgeHandler) { slot -> server.publish(slot) }
    private val server: DsuServer = DsuServer(registry, access)

    @Volatile
    private var status: ServerStatus = ServerStatus.Stopped

    @Volatile
    private var localAddress: String? = null

    /** Called on the main thread when the server stops by itself. */
    var onStoppedByFailure: () -> Unit = {}

    /** The interface list is read off the main thread; the screens only see the cached result. */
    private val addressRefresh = object : Runnable {
        override fun run() {
            localAddress = LocalAddress.find()
            bridgeHandler.postDelayed(this, ADDRESS_REFRESH_MS)
        }
    }

    private val batteryRefresh = object : Runnable {
        override fun run() {
            registry.refreshBatteries()
            mainHandler.postDelayed(this, BATTERY_REFRESH_MS)
        }
    }

    init {
        server.onFailure = {
            mainHandler.post {
                registry.setStreaming(false)
                status = ServerStatus.NetworkError
                onStoppedByFailure()
            }
        }
        registry.open()
        bridgeHandler.post(addressRefresh)
        mainHandler.postDelayed(batteryRefresh, BATTERY_REFRESH_MS)
    }

    val isRunning: Boolean get() = server.isRunning

    /** Starts the server on the configured port. Returns false and sets the status on failure. */
    fun start(): Boolean {
        if (server.isRunning) return true
        val port = settings.port
        try {
            server.start(port)
        } catch (_: BindException) {
            status = ServerStatus.PortInUse(port)
            return false
        } catch (_: SocketException) {
            status = ServerStatus.NetworkError
            return false
        }
        status = ServerStatus.Running
        registry.setStreaming(true)
        return true
    }

    fun stop() {
        registry.setStreaming(false)
        server.stop()
        status = ServerStatus.Stopped
    }

    /** Releases everything. The bridge cannot be used afterwards. */
    fun close() {
        stop()
        mainHandler.removeCallbacks(batteryRefresh)
        registry.close()
        bridgeThread.quitSafely()
    }

    fun state(): BridgeState = BridgeState(
        status = status,
        port = settings.port,
        localAddress = localAddress,
        slots = registry.views(),
        waitingJoyCons = registry.waitingCount,
        subscribers = server.subscribedAddresses(),
        pendingClient = access.pending().firstOrNull(),
        approvedClients = access.approved(),
    )

    fun onKeyEvent(event: KeyEvent): Boolean = registry.onKeyEvent(event)

    fun onMotionEvent(event: MotionEvent): Boolean = registry.onMotionEvent(event)

    fun refreshLights() = registry.refreshLights()

    fun approveClient(address: String) {
        access.approve(address)
        saveClients()
    }

    fun ignoreClient(address: String) = access.ignore(address)

    fun removeClient(address: String) {
        access.remove(address)
        saveClients()
    }

    /** Adds an address typed by the user. Only a literal IPv4 address is accepted. */
    fun addClient(text: String): AddClientResult {
        val address = Ipv4Address.normalize(text) ?: return AddClientResult.INVALID
        if (!access.approve(address)) return AddClientResult.ALREADY_ALLOWED
        saveClients()
        return AddClientResult.ADDED
    }

    /** Saves a new port and, when running, restarts the server on it. */
    fun changePort(port: Int) {
        require(PortRule.isValid(port)) { "Port out of range: $port" }
        if (port == settings.port) return
        settings.port = port
        if (server.isRunning) {
            stop()
            if (!start()) onStoppedByFailure()
        }
    }

    private fun saveClients() {
        settings.approvedClients = access.approved()
    }

    private companion object {
        const val ADDRESS_REFRESH_MS = 2_000L
        const val BATTERY_REFRESH_MS = 10_000L
    }
}
