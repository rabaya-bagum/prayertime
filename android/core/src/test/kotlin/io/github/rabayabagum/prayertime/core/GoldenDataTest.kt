package io.github.rabayabagum.prayertime.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the shared fixtures in /shared/testdata through PrayerCalculator. The iOS
 * suite reads the same files, so the two apps are held to the same numbers.
 */
class GoldenDataTest {
    private val testdata = File(System.getProperty("testdata.dir"))
    private val prayers = mapOf(
        "fajr" to Prayer.FAJR, "sunrise" to Prayer.SUNRISE, "dhuhr" to Prayer.DHUHR,
        "asr" to Prayer.ASR, "maghrib" to Prayer.MAGHRIB, "isha" to Prayer.ISHA,
    )

    @Test
    fun adhanTestdataMatchesPublishedTimetables() {
        val methods = mapOf(
            "MoonsightingCommittee" to Method.MOONSIGHTING_COMMITTEE,
            "UmmAlQura" to Method.UMM_AL_QURA,
            "Dubai" to Method.DUBAI,
            "Qatar" to Method.QATAR,
            "Kuwait" to Method.KUWAIT,
            "Singapore" to Method.SINGAPORE,
            "Turkey" to Method.TURKEY,
        )
        val rules = mapOf(
            "MiddleOfTheNight" to HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
            "SeventhOfTheNight" to HighLatitudeRule.SEVENTH_OF_THE_NIGHT,
            "TwilightAngle" to HighLatitudeRule.TWILIGHT_ANGLE,
        )
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val files = testdata.resolve("adhan").listFiles { f -> f.extension == "json" }!!.sorted()
        var checked = 0
        for (file in files) {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            val params = json.getValue("params").jsonObject
            val method = methods[params.string("method")] ?: continue // Tehran: see README
            val place = Place(
                file.nameWithoutExtension,
                params.getValue("latitude").jsonPrimitive.double,
                params.getValue("longitude").jsonPrimitive.double,
                params.string("timezone"),
            )
            val settings = PrayerSettings(
                method = method,
                madhab = if (params.string("madhab") == "Hanafi") Madhab.HANAFI else Madhab.SHAFI,
                highLatitudeRule = rules.getValue(params.string("highLatitudeRule")),
            )
            val variance = json["variance"]?.jsonPrimitive?.int ?: 0
            for (row in json.getValue("times").jsonArray.map { it.jsonObject }) {
                val date = LocalDate.parse(row.string("date"))
                val day = PrayerCalculator.day(place, date, settings)
                for ((key, prayer) in prayers) {
                    val expected = LocalTime.parse(row.string(key), clock)
                    assertWithin(variance, expected, day, prayer, "${file.name} $date $key")
                }
                checked++
            }
        }
        assertTrue(checked > 400, "expected the full Adhan data set, checked $checked days")
    }

    @Test
    fun independentReferenceTimesAgree() {
        val json = Json.parseToJsonElement(testdata.resolve("reference-praytimes.json").readText()).jsonObject
        val tolerance = json.getValue("toleranceMinutes").jsonPrimitive.int
        val cases = json.getValue("cases").jsonArray.map { it.jsonObject }
        for (case in cases) {
            val place = Place(
                case.string("id"),
                case.getValue("latitude").jsonPrimitive.double,
                case.getValue("longitude").jsonPrimitive.double,
                case.string("timeZone"),
            )
            val day = PrayerCalculator.day(place, LocalDate.parse(case.string("date")), settingsFor(case))
            val expected = case.getValue("expected").jsonObject
            for ((key, prayer) in prayers) {
                assertWithin(tolerance, LocalTime.parse(expected.string(key)), day, prayer, "${case.string("id")} $key")
            }
        }
        assertTrue(cases.size >= 20)
    }

    /** Method ids are Method.id, or "custom:<fajr>:<isha>". */
    private fun settingsFor(case: JsonObject): PrayerSettings {
        val id = case.string("method")
        val madhab = if (case.string("madhab") == "hanafi") Madhab.HANAFI else Madhab.SHAFI
        if (id.startsWith("custom:")) {
            val (_, fajr, isha) = id.split(":")
            return PrayerSettings(Method.CUSTOM, madhab, customAngles = CustomAngles(fajr.toDouble(), isha.toDouble()))
        }
        return PrayerSettings(Method.fromId(id), madhab)
    }

    private fun assertWithin(minutes: Int, expected: LocalTime, day: DaySchedule, prayer: Prayer, label: String) {
        val actual = day[prayer].atZone(day.place.zone).toLocalTime()
        val diff = abs(Duration.between(expected, actual).toMinutes())
        // Guard against midnight wrap (e.g. 23:59 vs 00:00).
        if (minOf(diff, 1440 - diff) > minutes) fail("$label: expected $expected ±$minutes min, got $actual")
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
