package io.github.rabayabagum.prayertime.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrayerCalculatorTest {
    private val toronto = Place("Toronto", 43.6532, -79.3832, "America/Toronto")
    private val makkah = Place("Makkah", 21.4225, 39.8262, "Asia/Riyadh")
    private val oslo = Place("Oslo", 59.9139, 10.7522, "Europe/Oslo")
    private val reykjavik = Place("Reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik")
    private val tromso = Place("Tromsø", 69.6492, 18.9553, "Europe/Oslo")
    private val kathmandu = Place("Kathmandu", 27.7172, 85.3240, "Asia/Kathmandu")
    private val kiritimati = Place("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati") // UTC+14
    private val apia = Place("Apia", -13.8333, -171.7667, "Pacific/Apia")
    private val sydney = Place("Sydney", -33.8688, 151.2093, "Australia/Sydney")

    private val mwl = PrayerSettings(Method.MUSLIM_WORLD_LEAGUE)

    @Test
    fun everyDayOfTheYearIsOrderedForManyPlacesAndMethods() {
        val places = listOf(toronto, makkah, oslo, reykjavik, tromso, kathmandu, kiritimati, apia, sydney)
        val methods = listOf(Method.MUSLIM_WORLD_LEAGUE, Method.ISNA, Method.MOONSIGHTING_COMMITTEE, Method.UMM_AL_QURA)
        for (place in places) for (method in methods) {
            var date = LocalDate.of(2026, 1, 1)
            while (date.year == 2026) {
                val day = PrayerCalculator.day(place, date, PrayerSettings(method))
                val times = Prayer.entries.map { day[it] }
                assertEquals(times.sorted(), times, "${place.name} $method $date out of order")
                assertEquals(date, day[Prayer.DHUHR].atZone(place.zone).toLocalDate(), "${place.name} $date Dhuhr on wrong day")
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun torontoFajrSpreadsAbout48MinutesAcrossMasjidConventions() {
        val date = LocalDate.of(2026, 5, 15)
        fun fajr(angle: Double) = PrayerCalculator.day(
            toronto, date, PrayerSettings(Method.CUSTOM, customAngles = CustomAngles(angle, angle)),
        )[Prayer.FAJR]
        val spread = Duration.between(fajr(18.0), fajr(12.0)).toMinutes()
        assertTrue(spread in 40..56, "12° vs 18° spread was $spread min")
        assertTrue(fajr(15.0) in fajr(18.0)..fajr(12.0))
    }

    @Test
    fun hanafiAsrIsLater() {
        val date = LocalDate.of(2026, 6, 1)
        val shafi = PrayerCalculator.day(toronto, date, mwl)[Prayer.ASR]
        val hanafi = PrayerCalculator.day(toronto, date, mwl.copy(madhab = Madhab.HANAFI))[Prayer.ASR]
        assertTrue(Duration.between(shafi, hanafi).toMinutes() in 30..90)
    }

    @Test
    fun dstChangeoverMovesLocalClockButNotTheSun() {
        val before = PrayerCalculator.day(toronto, LocalDate.of(2026, 3, 7), mwl)
        val after = PrayerCalculator.day(toronto, LocalDate.of(2026, 3, 8), mwl)
        // Solar noon drifts by seconds per day, so the UTC instants barely move...
        val utcDrift = Duration.between(before[Prayer.DHUHR].plus(Duration.ofDays(1)), after[Prayer.DHUHR]).abs()
        assertTrue(utcDrift.toMinutes() < 2)
        // ...while the wall clock jumps forward an hour.
        val localBefore = before[Prayer.DHUHR].atZone(toronto.zone).toLocalTime()
        val localAfter = after[Prayer.DHUHR].atZone(toronto.zone).toLocalTime()
        assertTrue(Duration.between(localBefore, localAfter).toMinutes() in 59L..61L)
    }

    @Test
    fun osloSummerUsesSeventhOfTheNightAutomatically() {
        val day = PrayerCalculator.day(oslo, LocalDate.of(2026, 6, 21), mwl)
        assertEquals(Adjustment.Calculated(HighLatitudeRule.SEVENTH_OF_THE_NIGHT), day.adjustment)
        // Twilight never ends at 18°, so Isha is capped at sunset + 1/7 of the night.
        val tomorrowSunrise = PrayerCalculator.day(oslo, LocalDate.of(2026, 6, 22), mwl)[Prayer.SUNRISE]
        val night = Duration.between(day[Prayer.MAGHRIB], tomorrowSunrise)
        val ishaGap = Duration.between(day[Prayer.MAGHRIB], day[Prayer.ISHA])
        assertTrue(ishaGap <= night.dividedBy(7).plusMinutes(1), "Isha gap $ishaGap, night $night")
    }

    @Test
    fun midnightSunAndPolarNightFallBackToNearestLatitude() {
        for (date in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
            val day = PrayerCalculator.day(tromso, date, mwl)
            val adjustment = assertIs<Adjustment.PolarFallback>(day.adjustment, "Tromsø $date")
            assertTrue(adjustment.latitudeUsed < tromso.latitude && adjustment.latitudeUsed > 60.0)
        }
        // Outside those seasons Tromsø calculates normally.
        assertIs<Adjustment.Calculated>(PrayerCalculator.day(tromso, LocalDate.of(2026, 3, 20), mwl).adjustment)
    }

    @Test
    fun southernHemisphereLatitudeRuleIsMiddleOfTheNight() {
        val day = PrayerCalculator.day(sydney, LocalDate.of(2026, 6, 21), mwl)
        assertEquals(Adjustment.Calculated(HighLatitudeRule.MIDDLE_OF_THE_NIGHT), day.adjustment)
    }

    @Test
    fun ummAlQuraIshaIs120MinutesInRamadanFollowingAdjustedHijriDate() {
        val settings = PrayerSettings(Method.UMM_AL_QURA)
        fun ishaGap(date: LocalDate, s: PrayerSettings = settings): Long {
            val day = PrayerCalculator.day(makkah, date, s)
            return Duration.between(day[Prayer.MAGHRIB], day[Prayer.ISHA]).toMinutes()
        }
        val midRamadan = LocalDate.of(2026, 3, 1)
        assertTrue(HijriDate.of(midRamadan).isRamadan)
        assertEquals(120, ishaGap(midRamadan))
        assertEquals(90, ishaGap(LocalDate.of(2026, 4, 15)))

        // 1 Ramadan 1447 by Umm al-Qura. With a -1 day sighting offset it is still Sha'ban.
        val firstDay = LocalDate.of(2026, 2, 18)
        assertEquals(HijriDate(1447, 9, 1), HijriDate.of(firstDay))
        assertEquals(120, ishaGap(firstDay))
        assertEquals(90, ishaGap(firstDay, settings.copy(hijriOffsetDays = -1)))
    }

    @Test
    fun userOffsetsAndImsak() {
        val date = LocalDate.of(2026, 5, 15)
        val plain = PrayerCalculator.day(toronto, date, mwl)
        val shifted = PrayerCalculator.day(toronto, date, mwl.copy(offsetMinutes = mapOf(Prayer.ASR to 3, Prayer.ISHA to -2)))
        assertEquals(plain[Prayer.ASR].plusSeconds(180), shifted[Prayer.ASR])
        assertEquals(plain[Prayer.ISHA].minusSeconds(120), shifted[Prayer.ISHA])
        assertEquals(plain[Prayer.FAJR], shifted[Prayer.FAJR])
        assertEquals(plain[Prayer.FAJR].minusSeconds(600), plain.imsak)
        assertTrue(plain.lastThirdOfTheNight > plain.middleOfTheNight)
    }

    @Test
    fun upcomingCrossesMidnightInOrder() {
        val lateEvening = ZonedDateTime.of(LocalDate.of(2026, 5, 15), LocalTime.of(23, 30), toronto.zone).toInstant()
        val next = PrayerCalculator.upcoming(toronto, mwl, lateEvening, 3)
        assertEquals(listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR), next.map { it.prayer })
        assertTrue(next.all { it.date == LocalDate.of(2026, 5, 16) })
    }

    @Test
    fun upcomingIsStrictlyIncreasingAcrossDstAndTheDateLine() {
        val cases = listOf(
            toronto to ZonedDateTime.of(2026, 10, 28, 0, 30, 0, 0, toronto.zone).toInstant(), // DST ends Nov 1
            kiritimati to ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, kiritimati.zone).toInstant(),
            apia to ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, apia.zone).toInstant(),
            kathmandu to ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, kathmandu.zone).toInstant(),
        )
        for ((place, from) in cases) {
            val events = PrayerCalculator.upcoming(place, mwl, from, 60)
            assertEquals(60, events.size)
            assertTrue(events.first().time > from)
            assertTrue(events.zipWithNext().all { (a, b) -> a.time < b.time }, place.name)
            // Exactly six events per day, so 60 events cover 10 consecutive days.
            assertEquals(10, events.map { it.date }.distinct().size, place.name)
        }
    }

    @Test
    fun qiblaFromTorontoPointsNortheast() {
        val bearing = PrayerCalculator.qiblaBearing(toronto.latitude, toronto.longitude)
        assertTrue(abs(bearing - 54.6) < 0.5, "Toronto Qibla was $bearing")
    }

    @Test
    fun countryDefaults() {
        assertEquals(Method.MOONSIGHTING_COMMITTEE, Defaults.settingsFor("ca").method)
        assertEquals(PrayerSettings(Method.KARACHI, madhab = Madhab.HANAFI), Defaults.settingsFor("PK"))
        assertEquals(Method.UMM_AL_QURA, Defaults.settingsFor("SA").method)
        assertEquals(Method.MUSLIM_WORLD_LEAGUE, Defaults.settingsFor(null).method)
    }
}
