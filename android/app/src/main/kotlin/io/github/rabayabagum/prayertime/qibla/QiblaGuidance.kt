package io.github.rabayabagum.prayertime.qibla

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Turns a true heading into "turn left/right" guidance toward the Qibla. Same rules as iOS. */
data class QiblaGuidance(
    /** Degrees to turn, (−180, 180]: positive is clockwise (right). */
    val turn: Double,
    val isAligned: Boolean,
) {
    val instruction: String
        get() {
            if (isAligned) return "Facing the Qibla"
            val degrees = abs(turn).roundToInt()
            return if (turn > 0) "Turn right $degrees°" else "Turn left $degrees°"
        }

    companion object {
        /**
         * Inside this the user is facing the Qibla; they must drift past [EXIT_DEGREES]
         * before it switches off, so the haptic doesn't chatter at the edge.
         */
        const val ENTER_DEGREES = 3.0
        const val EXIT_DEGREES = 6.0

        fun of(qiblaBearing: Double, trueHeading: Double, wasAligned: Boolean = false): QiblaGuidance {
            val turn = normalized(qiblaBearing - trueHeading)
            return QiblaGuidance(turn, abs(turn) <= if (wasAligned) EXIT_DEGREES else ENTER_DEGREES)
        }

        fun normalized(degrees: Double): Double {
            var d = degrees % 360
            if (d > 180) d -= 360
            if (d <= -180) d += 360
            return d
        }
    }
}

/**
 * How far to trust the magnetometer. Android reports a status rather than
 * degrees (SensorManager.SENSOR_STATUS_*), so this maps that to iOS's levels.
 */
enum class HeadingQuality(val summary: String, val needsCalibration: Boolean) {
    CALIBRATING("Calibrating", true),
    POOR("Low", true),
    FAIR("Medium", false),
    GOOD("High", false);

    companion object {
        // SensorManager.SENSOR_STATUS_UNRELIABLE = 0, ACCURACY_LOW = 1, MEDIUM = 2, HIGH = 3.
        fun fromSensorStatus(status: Int): HeadingQuality = when (status) {
            3 -> GOOD
            2 -> FAIR
            1 -> POOR
            else -> CALIBRATING
        }
    }
}

/** "10.2° W" / "3.4° E" for declination, east positive. */
val Double.declinationText: String
    get() = "%.1f° %s".format(Locale.ROOT, abs(this), if (this < 0) "W" else "E")
