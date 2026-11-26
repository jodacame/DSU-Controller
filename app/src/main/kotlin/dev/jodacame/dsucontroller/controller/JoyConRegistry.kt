// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.jodacame.dsucontroller.protocol.DsuBattery
import dev.jodacame.dsucontroller.protocol.DsuProtocol
import dev.jodacame.dsucontroller.protocol.PadFrame
import dev.jodacame.dsucontroller.protocol.SlotInfo
import dev.jodacame.dsucontroller.server.PadSource
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.roundToInt

/**
 * What the screen shows about a slot that holds a Joy-Con.
 *
 * @property batteryLevel the charge level, [DsuBattery.NONE] when Android does not report it
 * @property motionArriving false when no motion sample came in the last two seconds, after a
 *   grace period from Start. A connected Joy-Con sends samples all the time, even lying still,
 *   so silence always means the phone stopped delivering them.
 */
data class SlotView(
    val side: JoyConSide,
    val batteryLevel: DsuBattery,
    val charging: Boolean,
    val motionArriving: Boolean,
    val buttonPressed: Boolean,
)

/**
 * Tracks the Joy-Con connected to the phone: gives each a slot, shows the slot on its player
 * LEDs, keeps its live input and, while streaming, listens to its motion sensors.
 *
 * Device changes, keys and stick arrive on the main thread; motion arrives on [bridgeHandler]'s
 * thread, which is also where [onSample] is called. The [PadSource] methods are thread-safe.
 *
 * @param onSample called when a slot has new input to send
 */
class JoyConRegistry(
    context: Context,
    private val bridgeHandler: Handler,
    private val onSample: (slot: Int) -> Unit,
) : PadSource, InputManager.InputDeviceListener {

    private inner class Connected(
        val device: InputDevice,
        val side: JoyConSide,
        val slot: Int,
    ) : SensorEventListener {
        val descriptor: String = device.descriptor
        val mac = SlotTable.stableMac(descriptor)
        val state = PadState(side)
        val lights = PlayerLights(device)
        private val sensors = device.sensorManager
        private val gyroscope: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        private val accelerometer: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        /** Both sensors report at the same rate; one packet goes out per sample of this one. */
        private val pacingSensorType = (gyroscope ?: accelerometer)?.type

        @Volatile var batteryLevel = DsuBattery.NONE
        @Volatile var charging = false
        @Volatile var lastMotionNanos = MotionStall.NEVER
        @Volatile var lastPublishNanos = 0L

        /** When streaming turned the sensors on; the grace period of the dashboard counts from here. */
        @Volatile var listeningSinceNanos = 0L

        /** When the listeners were last registered, by Start or by a stall restart. */
        @Volatile var registeredSinceNanos = 0L

        /** Counters since the last rate log; touched on the bridge thread only. */
        var samplesSinceLog = 0
        var restartsSinceLog = 0

        /** Call from the main thread. */
        fun listen() {
            listeningSinceNanos = SystemClock.elapsedRealtimeNanos()
            register()
        }

        /** Call from the main thread. */
        fun stopListening() {
            sensors.unregisterListener(this)
            lastMotionNanos = MotionStall.NEVER
        }

        /**
         * Turns the sensors off and on again. Turning them off is what clears the platform's stuck
         * state (see [MotionStall]). Call from the main thread.
         */
        fun restartListening() {
            sensors.unregisterListener(this)
            register()
        }

        /** True when the sensors were registered for a while and no sample came since. */
        fun motionStalled(now: Long): Boolean = MotionStall.stalled(now, lastMotionNanos, registeredSinceNanos)

        private fun register() {
            registeredSinceNanos = SystemClock.elapsedRealtimeNanos()
            for (sensor in listOfNotNull(gyroscope, accelerometer)) {
                sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, bridgeHandler)
            }
        }

        fun readBattery() {
            val battery = device.batteryState
            batteryLevel = JoyConMapping.batteryLevel(battery.isPresent, battery.capacity)
            charging = JoyConMapping.battery(battery.isPresent, battery.capacity, battery.status) == DsuBattery.CHARGING
        }

        override fun onSensorChanged(event: SensorEvent) {
            // Arrival time on the phone's clock: the event's own timestamp is not reliable.
            val now = SystemClock.elapsedRealtimeNanos()
            val micros = now / NANOS_PER_MICRO
            val values = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> state.onGyroscope(values[0], values[1], values[2], micros)
                Sensor.TYPE_ACCELEROMETER -> state.onAccelerometer(values[0], values[1], values[2], micros)
                else -> return
            }
            lastMotionNanos = now
            samplesSinceLog++
            if (event.sensor.type == pacingSensorType) publish(this, now)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val inputManager = context.getSystemService(InputManager::class.java)
    private val mainHandler = Handler(context.mainLooper)
    private val slotTable = SlotTable(DsuProtocol.SLOT_COUNT)
    private val bySlot = AtomicReferenceArray<Connected?>(DsuProtocol.SLOT_COUNT)
    private val byDeviceId = HashMap<Int, Connected>()

    @Volatile
    private var streaming = false

    /** Joy-Con that are connected but found all four slots taken. */
    @Volatile
    var waitingCount = 0
        private set

    /**
     * Without motion, nothing else would send a packet: this keeps every connected slot sending
     * its buttons and stick at a steady low rate, so one lost datagram cannot leave a button
     * stuck on the emulator's side.
     */
    private val heartbeat = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtimeNanos()
            for (slot in 0 until DsuProtocol.SLOT_COUNT) {
                val connected = bySlot.get(slot) ?: continue
                if (now - connected.lastPublishNanos >= HEARTBEAT_MS * NANOS_PER_MILLI) publish(connected, now)
                if (connected.motionStalled(now)) {
                    // Marked now so the next beats do not ask again while the restart is queued.
                    connected.registeredSinceNanos = now
                    connected.restartsSinceLog++
                    mainHandler.post { restartStalled(connected) }
                }
            }
            if (now - lastRateLogNanos >= RATE_LOG_NANOS) logRates(now)
            bridgeHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private var lastRateLogNanos = 0L

    /**
     * Motion samples per second per slot, so a stalled sensor shows up in
     * `adb logcat -s JoyConRegistry`. A warning when the sensors of a slot had to be restarted.
     */
    private fun logRates(now: Long) {
        val seconds = (now - lastRateLogNanos) / NANOS_PER_SECOND.toFloat()
        lastRateLogNanos = now
        var restarted = false
        val rates = (0 until DsuProtocol.SLOT_COUNT).mapNotNull { slot ->
            bySlot.get(slot)?.let { connected ->
                val rate = connected.samplesSinceLog / seconds
                val restarts = connected.restartsSinceLog
                connected.samplesSinceLog = 0
                connected.restartsSinceLog = 0
                restarted = restarted || restarts > 0
                val note = if (restarts > 0) " (sensors restarted $restarts times)" else ""
                "P${slot + 1} ${connected.side.name.lowercase()} ${rate.roundToInt()}/s$note"
            }
        }
        if (rates.isEmpty()) return
        val line = "Motion samples: ${rates.joinToString(", ")}"
        if (restarted) Log.w(TAG, line) else Log.i(TAG, line)
    }

    /** Runs on the main thread, like every other registration, so it cannot race with Stop. */
    private fun restartStalled(connected: Connected) {
        if (streaming && byDeviceId[connected.device.id] === connected) connected.restartListening()
    }

    /** Starts tracking devices. Call from the main thread. */
    fun open() {
        inputManager.registerInputDeviceListener(this, mainHandler)
        rescan()
    }

    fun close() {
        setStreaming(false)
        inputManager.unregisterInputDeviceListener(this)
        byDeviceId.keys.toList().forEach(::forget)
    }

    /** Turns the motion sensors and the sending of input on or off. */
    fun setStreaming(enabled: Boolean) {
        if (streaming == enabled) return
        streaming = enabled
        if (enabled) {
            byDeviceId.values.forEach { it.listen() }
            bridgeHandler.post(heartbeat)
        } else {
            bridgeHandler.removeCallbacks(heartbeat)
            byDeviceId.values.forEach { it.stopListening() }
        }
    }

    /** Returns true when the event came from a tracked Joy-Con and must not reach the UI. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val connected = byDeviceId[event.deviceId] ?: return false
        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        if (connected.state.onKey(event.keyCode, pressed)) publishSoon(connected)
        return true
    }

    /** Returns true when the event came from a tracked Joy-Con and must not reach the UI. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        val connected = byDeviceId[event.deviceId] ?: return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            val axes = JoyConMapping.stickAxes(connected.side)
            connected.state.onStick(event.getAxisValue(axes.x), event.getAxisValue(axes.y))
            publishSoon(connected)
        }
        return true
    }

    /** Reads the battery of every Joy-Con. Call from the main thread, a few times a minute. */
    fun refreshBatteries() = byDeviceId.values.forEach { it.readBattery() }

    /** Puts every slot number back on its LEDs, in case another screen changed them. */
    fun refreshLights() = byDeviceId.values.forEach { it.lights.show(it.slot + 1) }

    /** One entry per slot, null where the slot is free. */
    fun views(): List<SlotView?> {
        val now = SystemClock.elapsedRealtimeNanos()
        return List(DsuProtocol.SLOT_COUNT) { slot ->
            bySlot.get(slot)?.let { connected ->
                SlotView(
                    side = connected.side,
                    batteryLevel = connected.batteryLevel,
                    charging = connected.charging,
                    motionArriving = motionArriving(connected, now),
                    buttonPressed = connected.state.anyButtonPressed,
                )
            }
        }
    }

    private fun motionArriving(connected: Connected, now: Long): Boolean =
        MotionStall.fresh(now, connected.lastMotionNanos) ||
            (streaming && now - connected.listeningSinceNanos < FIRST_MOTION_GRACE_NANOS)

    override fun slotInfo(slot: Int): SlotInfo {
        val connected = bySlot.get(slot) ?: return SlotInfo.empty(slot)
        val battery = if (connected.charging) DsuBattery.CHARGING else connected.batteryLevel
        return SlotInfo(slot, connected = true, mac = connected.mac, battery = battery)
    }

    override fun frame(slot: Int): PadFrame? = bySlot.get(slot)?.state?.frame()

    override fun onInputDeviceAdded(deviceId: Int) = rescan()

    override fun onInputDeviceRemoved(deviceId: Int) {
        forget(deviceId)
        // A Joy-Con that was waiting for a slot can take the one that was just freed.
        rescan()
    }

    override fun onInputDeviceChanged(deviceId: Int) = Unit

    private fun rescan() {
        var waiting = 0
        for (deviceId in inputManager.inputDeviceIds) {
            if (byDeviceId.containsKey(deviceId)) continue
            val device = inputManager.getInputDevice(deviceId) ?: continue
            val side = JoyConSide.fromIds(device.vendorId, device.productId) ?: continue
            val slot = slotTable.assign(device.descriptor)
            if (slot == null) {
                waiting++
                continue
            }
            val connected = Connected(device, side, slot)
            byDeviceId[deviceId] = connected
            bySlot.set(slot, connected)
            connected.readBattery()
            connected.lights.show(slot + 1)
            if (streaming) connected.listen()
        }
        waitingCount = waiting
    }

    private fun forget(deviceId: Int) {
        val connected = byDeviceId.remove(deviceId) ?: return
        // A reconnecting Joy-Con may already hold this slot under a new device id.
        if (bySlot.compareAndSet(connected.slot, connected, null)) slotTable.release(connected.descriptor)
        connected.stopListening()
        connected.lights.close()
    }

    /** Keys and stick arrive on the main thread, which must not touch the network. */
    private fun publishSoon(connected: Connected) {
        if (streaming) bridgeHandler.post { publish(connected, SystemClock.elapsedRealtimeNanos()) }
    }

    private fun publish(connected: Connected, now: Long) {
        connected.lastPublishNanos = now
        onSample(connected.slot)
    }

    private companion object {
        const val TAG = "JoyConRegistry"
        const val HEARTBEAT_MS = 20L
        const val RATE_LOG_NANOS = 5_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_MICRO = 1_000L
        const val FIRST_MOTION_GRACE_NANOS = 10_000_000_000L
    }
}
