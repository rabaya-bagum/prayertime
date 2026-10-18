package io.github.rabayabagum.prayertime.qibla

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The World Magnetic Model (NOAA/BGS, public domain), used to turn the compass's
 * magnetic heading into a true heading. A port of the iOS app's MagneticModel,
 * reading the same WMM2025.COF, so both apps give identical declinations.
 * Android's GeomagneticField isn't used because its coefficients are whatever
 * the OS release shipped with.
 *
 * Follows "The US/UK World Magnetic Model for 2025–2030" technical report:
 * geodetic → geocentric coordinates, a degree-12 spherical-harmonic sum with
 * Schmidt semi-normalized Legendre functions, then rotation back to geodetic.
 * Checked against NOAA's published test values in MagneticModelTest.
 */
class MagneticModel private constructor(
    val epoch: Double,
    val name: String,
    private val maxDegree: Int,
    // Indexed [n][m]; nT and nT/year.
    private val g: Array<DoubleArray>,
    private val h: Array<DoubleArray>,
    private val gDot: Array<DoubleArray>,
    private val hDot: Array<DoubleArray>,
) {
    data class Field(val north: Double, val east: Double, val down: Double)

    /** The model is only valid for five years from its epoch; NOAA publishes the next one. */
    val validYears: ClosedFloatingPointRange<Double> get() = epoch..(epoch + 5)

    /** Magnetic declination in degrees, east positive: true heading = magnetic heading + declination. */
    fun declination(latitude: Double, longitude: Double, altitudeKm: Double = 0.0, decimalYear: Double): Double {
        val f = field(latitude, longitude, altitudeKm, decimalYear)
        return Math.toDegrees(atan2(f.east, f.north))
    }

    fun declination(latitude: Double, longitude: Double, at: Instant): Double =
        declination(latitude, longitude, decimalYear = decimalYear(at))

    /** North, east and down components in nT, in the geodetic frame. */
    fun field(latitude: Double, longitude: Double, altitudeKm: Double, decimalYear: Double): Field {
        // WGS-84 ellipsoid and the model's reference radius.
        val a = 6378.137
        val flattening = 1 / 298.257223563
        val e2 = flattening * (2 - flattening)
        val referenceRadius = 6371.2

        // Geodetic → geocentric spherical (report eq. 7).
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val rc = a / sqrt(1 - e2 * sin(phi) * sin(phi))
        val p = (rc + altitudeKm) * cos(phi)
        val z = (rc * (1 - e2) + altitudeKm) * sin(phi)
        val r = sqrt(p * p + z * z)
        val phiPrime = asin(z / r)

        // Gauss coefficients at the requested time (eq. 9).
        val dt = decimalYear - epoch
        val n0 = maxDegree

        // Schmidt semi-normalized P(n,m)(sin φ') and dP/dφ', built from Gauss-normalized
        // recursions then scaled (Wertz, Spacecraft Attitude Determination, app. H).
        val theta = Math.PI / 2 - phiPrime // colatitude
        val cosT = cos(theta)
        val sinT = sin(theta)
        val pp = Array(n0 + 1) { DoubleArray(n0 + 1) }
        val dp = Array(n0 + 1) { DoubleArray(n0 + 1) }
        pp[0][0] = 1.0
        for (n in 1..n0) {
            for (m in 0..n) {
                if (n == m) {
                    pp[n][m] = sinT * pp[n - 1][m - 1]
                    dp[n][m] = sinT * dp[n - 1][m - 1] + cosT * pp[n - 1][m - 1]
                } else {
                    val k = if (n == 1) 0.0 else ((n - 1) * (n - 1) - m * m).toDouble() / ((2 * n - 1) * (2 * n - 3))
                    val p2 = if (n >= 2) pp[n - 2][m] else 0.0
                    val dp2 = if (n >= 2) dp[n - 2][m] else 0.0
                    pp[n][m] = cosT * pp[n - 1][m] - k * p2
                    dp[n][m] = cosT * dp[n - 1][m] - sinT * pp[n - 1][m] - k * dp2
                }
            }
        }
        val s = Array(n0 + 1) { DoubleArray(n0 + 1) }
        s[0][0] = 1.0
        for (n in 1..n0) {
            s[n][0] = s[n - 1][0] * (2 * n - 1) / n
            for (m in 1..n) {
                val factor = ((n - m + 1) * (if (m == 1) 2 else 1)).toDouble() / (n + m)
                s[n][m] = s[n][m - 1] * sqrt(factor)
            }
        }

        // Spherical-harmonic sums (eq. 10). With B = −∇V and θ the colatitude:
        // north X' = (1/r)∂V/∂θ, east Y' = −(1/(r sin θ))∂V/∂λ, down Z' = ∂V/∂r.
        var bNorth = 0.0
        var bEast = 0.0
        var bDown = 0.0
        var ratio = (referenceRadius / r) * (referenceRadius / r)
        for (n in 1..n0) {
            ratio *= referenceRadius / r // (a/r)^(n+2)
            for (m in 0..n) {
                val gnm = g[n][m] + dt * gDot[n][m]
                val hnm = h[n][m] + dt * hDot[n][m]
                val cosM = cos(m * lambda)
                val sinM = sin(m * lambda)
                val pnm = s[n][m] * pp[n][m]
                val dpnm = s[n][m] * dp[n][m]
                val gh = gnm * cosM + hnm * sinM
                bNorth += ratio * gh * dpnm
                bEast += ratio * m * (gnm * sinM - hnm * cosM) * pnm
                bDown -= (n + 1) * ratio * gh * pnm
            }
        }
        // Guard the poles, where declination is undefined anyway.
        bEast /= max(sinT, 1e-10)

        // Rotate from geocentric to geodetic (eq. 17).
        val psi = phiPrime - phi
        return Field(
            north = bNorth * cos(psi) - bDown * sin(psi),
            east = bEast,
            down = bNorth * sin(psi) + bDown * cos(psi),
        )
    }

    companion object {
        /** Parses NOAA's WMM.COF format: a header line, then "n m g h ġ ḣ" rows, ending in 9s. */
        fun parse(cof: String): MagneticModel? {
            val lines = cof.lines().filter { it.isNotBlank() }
            val header = lines.firstOrNull()?.trim()?.split(Regex("\\s+")) ?: return null
            if (header.size < 2) return null
            val epoch = header[0].toDoubleOrNull() ?: return null

            data class Row(val n: Int, val m: Int, val g: Double, val h: Double, val gd: Double, val hd: Double)
            val rows = mutableListOf<Row>()
            for (line in lines.drop(1)) {
                val f = line.trim().split(Regex("\\s+"))
                if (f.size != 6) break
                rows += Row(
                    f[0].toIntOrNull() ?: break, f[1].toIntOrNull() ?: break,
                    f[2].toDoubleOrNull() ?: break, f[3].toDoubleOrNull() ?: break,
                    f[4].toDoubleOrNull() ?: break, f[5].toDoubleOrNull() ?: break,
                )
            }
            val maxDegree = rows.maxOfOrNull { it.n }?.takeIf { it >= 1 } ?: return null
            fun grid() = Array(maxDegree + 1) { DoubleArray(maxDegree + 1) }
            val g = grid()
            val h = grid()
            val gDot = grid()
            val hDot = grid()
            for (row in rows) {
                if (row.m > row.n) continue
                g[row.n][row.m] = row.g
                h[row.n][row.m] = row.h
                gDot[row.n][row.m] = row.gd
                hDot[row.n][row.m] = row.hd
            }
            return MagneticModel(epoch, header[1], maxDegree, g, h, gDot, hDot)
        }

        fun decimalYear(instant: Instant): Double {
            val year = instant.atZone(ZoneOffset.UTC).year
            val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            return year + (instant.epochSecond - start.epochSecond).toDouble() / (end.epochSecond - start.epochSecond)
        }
    }
}
