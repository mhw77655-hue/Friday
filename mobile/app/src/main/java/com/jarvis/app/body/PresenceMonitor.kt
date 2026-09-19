package com.jarvis.app.body

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Presence monitor using sensors + interaction events.
 * Determines user presence without continuously running expensive vision processing.
 *
 * Uses:
 * - Proximity sensor (phone near face/ear)
 * - Light sensor (sudden changes = user interaction)
 * - Accelerometer (pickup/movement)
 * - Screen on/off (via interaction callback)
 * - Touch/interaction events (explicit user activity)
 *
 * Event-driven with appropriate throttling.
 */
private const val TAG = "PresenceMonitor"
private const val PROXIMITY_THRESHOLD = 5.0f // cm
private const val LIGHT_CHANGE_THRESHOLD = 100f // lux
private const val MOVEMENT_THRESHOLD = 2.0f // m/s²
private const val PRESENCE_CONFIRMATION_MS = 2000L
private const val ABSENCE_CONFIRMATION_MS = 10000L

class PresenceMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPresenceChanged: (Boolean) -> Unit
) {

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null

    private val _isPresent = MutableStateFlow(false)
    val isPresent: StateFlow<Boolean> = _isPresent.asStateFlow()

    private var lastProximity = Float.MAX_VALUE
    private var lastLight = 0f
    private var lastMovement = 0f
    private var lastUserInteraction = System.currentTimeMillis()
    private var presenceConfirmed = false
    private var confirmationTimer: Long = 0

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { e ->
                when (e.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> handleProximity(e.values[0])
                    Sensor.TYPE_LIGHT -> handleLight(e.values[0])
                    Sensor.TYPE_ACCELEROMETER -> handleMovement(e.values)
                }
                evaluatePresence()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        proximitySensor?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        // Periodic presence evaluation
        scope.launch {
            while (true) {
                delay(1000)
                evaluatePresence()
            }
        }

        Log.i(TAG, "Presence monitor started")
    }

    fun stop() {
        sensorManager?.unregisterListener(sensorListener)
        Log.i(TAG, "Presence monitor stopped")
    }

    /** Call this on explicit user interaction (touch, voice, button). */
    fun onUserInteraction() {
        lastUserInteraction = System.currentTimeMillis()
        if (!_isPresent.value) {
            // Immediate presence on explicit interaction
            setPresent(true)
        }
    }

    private fun handleProximity(value: Float) {
        lastProximity = value
    }

    private fun handleLight(value: Float) {
        val change = abs(value - lastLight)
        if (change > LIGHT_CHANGE_THRESHOLD) {
            lastUserInteraction = System.currentTimeMillis()
        }
        lastLight = value
    }

    private fun handleMovement(values: FloatArray) {
        val magnitude = kotlin.math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val change = abs(magnitude - lastMovement)
        if (change > MOVEMENT_THRESHOLD) {
            lastUserInteraction = System.currentTimeMillis()
        }
        lastMovement = magnitude
    }

    private fun evaluatePresence() {
        val now = System.currentTimeMillis()
        val timeSinceInteraction = now - lastUserInteraction

        // Proximity sensor: near = present (phone at ear/face)
        val proximityIndicatesPresent = lastProximity < PROXIMITY_THRESHOLD && lastProximity >= 0

        // Recent interaction = present
        val recentInteraction = timeSinceInteraction < PRESENCE_CONFIRMATION_MS

        val shouldBePresent = proximityIndicatesPresent || recentInteraction

        if (shouldBePresent && !presenceConfirmed) {
            confirmationTimer = if (confirmationTimer == 0L) now else confirmationTimer
            if (now - confirmationTimer >= PRESENCE_CONFIRMATION_MS) {
                presenceConfirmed = true
                setPresent(true)
            }
        } else if (!shouldBePresent && presenceConfirmed) {
            confirmationTimer = if (confirmationTimer == 0L) now else confirmationTimer
            if (now - confirmationTimer >= ABSENCE_CONFIRMATION_MS) {
                presenceConfirmed = false
                setPresent(false)
            }
        } else {
            confirmationTimer = 0
        }
    }

    private fun setPresent(present: Boolean) {
        if (_isPresent.value != present) {
            _isPresent.value = present
            onPresenceChanged(present)
            Log.i(TAG, "Presence changed: $present")
        }
    }
}