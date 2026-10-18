package io.github.rabayabagum.prayertime.qibla

import io.github.rabayabagum.prayertime.core.PrayerCalculator
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors the iOS MagneticModelTests and QiblaGuidanceTests. */
class QiblaTest {
    private val model = MagneticModel.parse(File(System.getProperty("wmm.cof")).readText())!!

    @Test
    fun loadsTheBundledCoefficients() {
        assertEquals(2025.0, model.epoch)
        assertEquals("WMM-2025", model.name)
    }

    /** NOAA's official WMM2025 test values, the same file the iOS tests use. */
    @Test
    fun matchesNoaaTestValues() {
        val rows = File(System.getProperty("wmm.testValues")).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.trim().split(Regex("\\s+")).map(String::toDouble) }
        assertTrue(rows.size >= 12)
        for (v in rows) {
            val (year, alt, lat, lon, decl) = v
            val f = model.field(lat, lon, alt, year)
            val label = "$year $lat,$lon @${alt}km"
            // Components to 0.1 nT; the file prints declination to 0.01°.
            assertClose(v[7], f.north, 0.1, "X $label")
            assertClose(v[8], f.east, 0.1, "Y $label")
            assertClose(v[9], f.down, 0.1, "Z $label")
            assertClose(decl, model.declination(lat, lon, alt, year), 0.01, "D $label")
        }
    }

    @Test
    fun torontoDeclinationIsAboutTenWest() {
        assertClose(-10.0, model.declination(43.65, -79.38, decimalYear = 2026.8), 1.5, "Toronto")
    }

    @Test
    fun decimalYear() {
        assertEquals(2025.0, MagneticModel.decimalYear(Instant.parse("2025-01-01T00:00:00Z")))
        assertClose(2026.5, MagneticModel.decimalYear(Instant.parse("2026-07-02T12:00:00Z")), 0.002, "mid-2026")
    }

    @Test
    fun turnIsTheShortWayRound() {
        assertEquals(55.0, QiblaGuidance.of(55.0, 0.0).turn)
        assertEquals(-45.0, QiblaGuidance.of(55.0, 100.0).turn)
        assertEquals(20.0, QiblaGuidance.of(10.0, 350.0).turn, "across north")
        assertEquals(-20.0, QiblaGuidance.of(350.0, 10.0).turn, "across north the other way")
        assertEquals(180.0, QiblaGuidance.of(180.0, 0.0).turn, "directly behind turns right")
    }

    @Test
    fun instructions() {
        assertEquals("Turn right 55°", QiblaGuidance.of(55.0, 0.0).instruction)
        assertEquals("Turn left 45°", QiblaGuidance.of(55.0, 100.0).instruction)
        assertEquals("Facing the Qibla", QiblaGuidance.of(55.0, 53.0).instruction)
    }

    @Test
    fun alignmentHasHysteresis() {
        assertTrue(QiblaGuidance.of(55.0, 52.0).isAligned)
        assertFalse(QiblaGuidance.of(55.0, 50.0).isAligned, "5° off doesn't enter")
        assertTrue(QiblaGuidance.of(55.0, 50.0, wasAligned = true).isAligned, "but doesn't leave either")
        assertFalse(QiblaGuidance.of(55.0, 48.0, wasAligned = true).isAligned, "7° off leaves")
    }

    @Test
    fun headingQualityFromSensorStatus() {
        assertEquals(HeadingQuality.CALIBRATING, HeadingQuality.fromSensorStatus(0))
        assertEquals(HeadingQuality.POOR, HeadingQuality.fromSensorStatus(1))
        assertEquals(HeadingQuality.FAIR, HeadingQuality.fromSensorStatus(2))
        assertEquals(HeadingQuality.GOOD, HeadingQuality.fromSensorStatus(3))
        assertTrue(HeadingQuality.POOR.needsCalibration)
        assertFalse(HeadingQuality.FAIR.needsCalibration)
    }

    @Test
    fun declinationText() {
        assertEquals("10.2° W", (-10.24).declinationText)
        assertEquals("3.5° E", 3.46.declinationText)
    }

    @Test
    fun magneticToTrueQiblaForToronto() {
        // A phone reading magnetic 65° in Toronto points about 55° true: almost exactly at the Qibla.
        val lat = 43.6532
        val lon = -79.3832
        val declination = model.declination(lat, lon, decimalYear = 2026.8)
        val qibla = PrayerCalculator.qiblaBearing(lat, lon)
        assertTrue(QiblaGuidance.of(qibla, 65 + declination).isAligned)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double, label: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$label: expected $expected ±$tolerance, got $actual")
}
