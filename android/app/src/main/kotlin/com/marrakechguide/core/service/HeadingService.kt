package com.marrakechguide.core.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Confidence level for compass heading readings.
 */
enum class HeadingConfidence {
    /** Heading is reliable and accurate */
    GOOD,
    /** Heading may be affected by magnetic interference */
    WEAK,
    /** Heading is not available (no sensors or significant interference) */
    UNAVAILABLE
}

/**
 * Interface for heading/compass services.
 *
 * This service wraps SensorManager rotation vector sensor with:
 * - Confidence indicators for heading reliability
 * - Battery-safe start/stop patterns
 * - UI update throttling (10-20Hz max)
 * - Automatic calibration detection
 */
interface HeadingService {
    /** Current heading in degrees (0-360, 0 = north), null if unavailable. */
    val currentHeading: StateFlow<Float?>

    /** Confidence level of the current heading reading. */
    val headingConfidence: StateFlow<HeadingConfidence>

    /** Whether heading services are available on this device. */
    val isHeadingAvailable: Boolean

    /**
     * Starts receiving heading updates.
     * Updates are throttled to preserve battery.
     * Call stopUpdates() when compass screen is no longer visible.
     */
    fun startUpdates()

    /**
     * Stops receiving heading updates.
     * Always call this when the compass screen closes.
     */
    fun stopUpdates()

    /**
     * Requests calibration by waving device in figure-8 pattern.
     * This is typically triggered by low accuracy readings.
     *
     * @return true if calibration is needed
     */
    fun needsCalibration(): Boolean
}

/**
 * Implementation of HeadingService using SensorManager.
 *
 * Uses TYPE_ROTATION_VECTOR sensor which fuses accelerometer, magnetometer,
 * and optionally gyroscope for stable heading readings.
 *
 * This implementation:
 * - Throttles updates to 20Hz max (50ms minimum interval)
 * - Includes automatic timeout after 10 minutes
 * - Detects low accuracy and signals calibration needed
 */
@Singleton
class HeadingServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HeadingService, SensorEventListener {

    companion object {
        private const val TAG = "HeadingService"
        private const val TIMEOUT_DURATION_MS = 600_000L // 10 minutes
        private const val MIN_UPDATE_INTERVAL_NS = 50_000_000L // 50ms = 20Hz
        private const val GOOD_ACCURACY_THRESHOLD = 15f // degrees
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val magneticSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _currentHeading = MutableStateFlow<Float?>(null)
    override val currentHeading: StateFlow<Float?> = _currentHeading.asStateFlow()

    private val _headingConfidence = MutableStateFlow(HeadingConfidence.UNAVAILABLE)
    override val headingConfidence: StateFlow<HeadingConfidence> = _headingConfidence.asStateFlow()

    override val isHeadingAvailable: Boolean
        get() = rotationSensor != null || magneticSensor != null

    private var isUpdating = false
    private var timeoutTimer: Timer? = null
    private var lastUpdateTimeNs = 0L

    // Rotation matrix and orientation arrays (reused to avoid allocation)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // For accelerometer/magnetometer fallback
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override fun startUpdates() {
        if (!isHeadingAvailable) {
            Log.i(TAG, "Heading services not available on this device")
            _headingConfidence.value = HeadingConfidence.UNAVAILABLE
            return
        }

        if (isUpdating) {
            Log.d(TAG, "Heading updates already active")
            return
        }

        isUpdating = true

        // Prefer rotation vector sensor (fused, more stable)
        if (rotationSensor != null) {
            sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            Log.i(TAG, "Started heading updates using rotation vector sensor")
        } else {
            // Fallback to accelerometer + magnetometer
            accelerometerSensor?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            magneticSensor?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            Log.i(TAG, "Started heading updates using accelerometer/magnetometer fallback")
        }

        // Set timeout to prevent indefinite battery drain
        resetTimeoutTimer()
    }

    override fun stopUpdates() {
        if (!isUpdating) return

        isUpdating = false
        sensorManager.unregisterListener(this)
        cancelTimeoutTimer()
        Log.i(TAG, "Stopped heading updates")
    }

    override fun needsCalibration(): Boolean {
        return _headingConfidence.value == HeadingConfidence.WEAK
    }

    // MARK: - SensorEventListener

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_MAGNETIC_FIELD -> handleMagnetometer(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        val confidence = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HeadingConfidence.GOOD
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> HeadingConfidence.GOOD
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> HeadingConfidence.WEAK
            SensorManager.SENSOR_STATUS_UNRELIABLE -> HeadingConfidence.WEAK
            else -> HeadingConfidence.UNAVAILABLE
        }

        _headingConfidence.value = confidence
        Log.d(TAG, "Sensor accuracy changed: $accuracy (confidence: $confidence)")
    }

    // MARK: - Private Methods

    private fun handleRotationVector(event: SensorEvent) {
        // Throttle updates to 20Hz
        if (shouldThrottleUpdate(event.timestamp)) return

        // Get rotation matrix from rotation vector
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Get orientation angles (azimuth is the first element)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert azimuth from radians to degrees (0-360)
        val azimuthRadians = orientationAngles[0]
        var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()

        // Normalize to 0-360 range
        if (azimuthDegrees < 0) {
            azimuthDegrees += 360f
        }

        updateHeading(azimuthDegrees)
    }

    private fun handleAccelerometer(event: SensorEvent) {
        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        hasAccelerometer = true
        tryComputeHeadingFromFallback(event.timestamp)
    }

    private fun handleMagnetometer(event: SensorEvent) {
        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        hasMagnetometer = true
        tryComputeHeadingFromFallback(event.timestamp)
    }

    private fun tryComputeHeadingFromFallback(timestamp: Long) {
        if (!hasAccelerometer || !hasMagnetometer) return

        // Throttle updates to 20Hz
        if (shouldThrottleUpdate(timestamp)) return

        // Compute rotation matrix from accelerometer and magnetometer
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        if (!success) {
            _headingConfidence.value = HeadingConfidence.WEAK
            return
        }

        // Get orientation angles
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert azimuth from radians to degrees (0-360)
        val azimuthRadians = orientationAngles[0]
        var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()

        // Normalize to 0-360 range
        if (azimuthDegrees < 0) {
            azimuthDegrees += 360f
        }

        updateHeading(azimuthDegrees)
    }

    private fun updateHeading(heading: Float) {
        _currentHeading.value = heading

        // If we don't have explicit accuracy info, assume good if we're getting updates
        if (_headingConfidence.value == HeadingConfidence.UNAVAILABLE) {
            _headingConfidence.value = HeadingConfidence.GOOD
        }

        Log.d(TAG, "Heading updated: $heading° (confidence: ${_headingConfidence.value})")

        // Reset timeout since we're getting updates
        if (isUpdating) {
            resetTimeoutTimer()
        }
    }

    private fun shouldThrottleUpdate(timestamp: Long): Boolean {
        if (lastUpdateTimeNs == 0L) {
            lastUpdateTimeNs = timestamp
            return false
        }

        val elapsed = timestamp - lastUpdateTimeNs
        if (elapsed >= MIN_UPDATE_INTERVAL_NS) {
            lastUpdateTimeNs = timestamp
            return false
        }

        return true
    }

    private fun resetTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    Log.w(TAG, "Heading updates timed out after ${TIMEOUT_DURATION_MS}ms")
                    stopUpdates()
                }
            }, TIMEOUT_DURATION_MS)
        }
    }

    private fun cancelTimeoutTimer() {
        timeoutTimer?.cancel()
        timeoutTimer = null
    }
}
