// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.controller

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import android.view.InputDevice

/**
 * Latest reading of one three-axis sensor of a Joy-Con, as Android delivers it.
 *
 * @property ratePerSecond samples per second, measured on the phone's clock at arrival
 * @property lastArrivalNanos arrival time of the last sample (elapsed realtime), or
 *   [SampleRateMeter.NO_TIMESTAMP]
 * @property sensorClockRatio how fast the samples' own timestamps advance compared with the
 *   phone's clock (1.0 is correct); NaN until two samples have arrived
 */
data class SensorReading(
    val available: Boolean,
    val ratePerSecond: Double,
    val totalSamples: Long,
    val lastArrivalNanos: Long,
    val sensorClockRatio: Double,
    val x: Float,
    val y: Float,
    val z: Float,
)

/** Everything the diagnostics screen shows about the sensors of one Joy-Con. */
data class ProbeSnapshot(
    val exposedSensorNames: List<String>,
    val gyroscope: SensorReading,
    val accelerometer: SensorReading,
)

/**
 * Listens to the motion sensors Android exposes for one Joy-Con and keeps their latest values
 * and delivery rate.
 *
 * Sensor callbacks arrive on the handler given to [start]; [snapshot] may be called from any
 * thread.
 */
class JoyConProbe(device: InputDevice) {
    private val sensorManager: SensorManager = device.sensorManager
    private val gyroscope = Channel(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE))
    private val accelerometer = Channel(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))
    private val exposedSensorNames = sensorManager.getSensorList(Sensor.TYPE_ALL).map { it.name }
    private val lock = Any()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val channel = when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> gyroscope
                Sensor.TYPE_ACCELEROMETER -> accelerometer
                else -> return
            }
            synchronized(lock) { channel.record(event) }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /** Starts listening at the fastest rate the platform offers. Safe to call once. */
    fun start(handler: Handler) {
        for (channel in listOf(gyroscope, accelerometer)) {
            val sensor = channel.sensor ?: continue
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST, handler)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    fun snapshot(): ProbeSnapshot = synchronized(lock) {
        ProbeSnapshot(exposedSensorNames, gyroscope.reading(), accelerometer.reading())
    }

    private class Channel(val sensor: Sensor?) {
        private val meter = SampleRateMeter()
        private var x = 0f
        private var y = 0f
        private var z = 0f
        private var firstArrivalNanos = SampleRateMeter.NO_TIMESTAMP
        private var firstSensorNanos = 0L
        private var lastSensorNanos = 0L

        /**
         * The rate is measured on the arrival clock, not on [SensorEvent.timestamp]: the
         * controller's timestamps are not guaranteed to tick at real-time speed.
         */
        fun record(event: SensorEvent) {
            val arrivalNanos = SystemClock.elapsedRealtimeNanos()
            if (firstArrivalNanos == SampleRateMeter.NO_TIMESTAMP) {
                firstArrivalNanos = arrivalNanos
                firstSensorNanos = event.timestamp
            }
            lastSensorNanos = event.timestamp
            meter.onSample(arrivalNanos)
            x = event.values[0]
            y = event.values[1]
            z = event.values[2]
        }

        fun reading() = SensorReading(
            available = sensor != null,
            ratePerSecond = meter.ratePerSecond,
            totalSamples = meter.totalSamples,
            lastArrivalNanos = meter.lastTimestampNanos,
            sensorClockRatio = sensorClockRatio(),
            x = x,
            y = y,
            z = z,
        )

        private fun sensorClockRatio(): Double {
            val arrivalSpan = meter.lastTimestampNanos - firstArrivalNanos
            if (firstArrivalNanos == SampleRateMeter.NO_TIMESTAMP || arrivalSpan <= 0) return Double.NaN
            return (lastSensorNanos - firstSensorNanos).toDouble() / arrivalSpan
        }
    }
}
