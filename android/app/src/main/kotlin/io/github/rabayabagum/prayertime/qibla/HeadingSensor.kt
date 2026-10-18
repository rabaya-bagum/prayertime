package io.github.rabayabagum.prayertime.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streams the magnetic heading of the phone's top edge, held flat. Needs no
 * permission; the screen converts it to true north with [MagneticModel].
 *
 * Uses the fused rotation-vector sensor when present, else the accelerometer
 * and magnetometer directly. Accuracy comes from the magnetometer's status.
 */
class HeadingSensor(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotationVector: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean = !testNoCompass &&
        (testHeading != null || magnetometer != null && (rotationVector != null || accelerometer != null))

    /** Degrees clockwise from magnetic north, or null before the first reading. */
    var magneticHeading by mutableStateOf<Double?>(null)
        private set

    var quality by mutableStateOf(HeadingQuality.CALIBRATING)
        private set

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false
    // Smoothed as a unit vector, so averaging across north (359° and 1°) gives 0°, not 180°.
    private var smoothX = 0.0
    private var smoothY = 0.0

    fun start() {
        testHeading?.let {
            magneticHeading = it
            quality = HeadingQuality.GOOD
            return
        }
        if (!isAvailable) return
        val primary = rotationVector ?: accelerometer
        manager.registerListener(this, primary, SensorManager.SENSOR_DELAY_UI)
        // Always listen to the magnetometer too: it reports the calibration status.
        manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager.unregisterListener(this)
        magneticHeading = null
        smoothX = 0.0
        smoothY = 0.0
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) quality = HeadingQuality.fromSensorStatus(accuracy)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                publish()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(gravity, endIndex = 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                quality = HeadingQuality.fromSensorStatus(event.accuracy)
                if (rotationVector == null) {
                    event.values.copyInto(geomagnetic, endIndex = 3)
                    hasGeomagnetic = true
                    if (hasGravity && SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) publish()
                }
            }
        }
    }

    private fun publish() {
        SensorManager.getOrientation(rotation, orientation)
        // Azimuth of the device's top edge; add the screen rotation so landscape still points ahead.
        val azimuth = Math.toDegrees(orientation[0].toDouble()) + displayRotationDegrees()
        val radians = Math.toRadians(azimuth)
        val alpha = if (magneticHeading == null) 1.0 else SMOOTHING
        smoothX += alpha * (cos(radians) - smoothX)
        smoothY += alpha * (sin(radians) - smoothY)
        magneticHeading = (Math.toDegrees(atan2(smoothY, smoothX)) + 360) % 360
    }

    @Suppress("DEPRECATION") // Context.display needs API 30; minSdk is 26.
    private fun displayRotationDegrees(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation
        } else {
            context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    companion object {
        /** Low-pass weight per reading at SENSOR_DELAY_UI (~60 ms): steady, but follows a turn within half a second. */
        private const val SMOOTHING = 0.25

        /** UI tests can't turn an emulator, so they pin the magnetic heading (as iOS's QIBLA_TEST_HEADING). */
        @VisibleForTesting
        var testHeading: Double? = null

        /** UI tests use this to stand in for a phone without a compass; emulators have one. */
        @VisibleForTesting
        var testNoCompass = false
    }
}
