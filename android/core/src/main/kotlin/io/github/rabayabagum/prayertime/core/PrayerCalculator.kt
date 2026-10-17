package io.github.rabayabagum.prayertime.core

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.data.DateComponents
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.sign
import com.batoulapps.adhan2.HighLatitudeRule as AdhanHighLatitudeRule
import com.batoulapps.adhan2.Madhab as AdhanMadhab
import com.batoulapps.adhan2.model.Shafaq as AdhanShafaq

/** How the day's times were obtained, so the UI can explain unusual values. */
sealed interface Adjustment {
    /** Normal calculation. [highLatitudeRule] is the rule in effect, after resolving AUTO. */
    data class Calculated(val highLatitudeRule: HighLatitudeRule) : Adjustment

    /**
     * The sun never set, never rose, or twilight never ended at this latitude on
     * this date, so times were computed at [latitudeUsed] ("nearest latitude").
     */
    data class PolarFallback(val latitudeUsed: Double, val highLatitudeRule: HighLatitudeRule) : Adjustment
}

data class DaySchedule(
    val place: Place,
    val date: LocalDate,
    val times: Map<Prayer, Instant>,
    val imsak: Instant,
    val middleOfTheNight: Instant,
    val lastThirdOfTheNight: Instant,
    val hijri: HijriDate,
    val method: Method,
    val adjustment: Adjustment,
) {
    operator fun get(prayer: Prayer): Instant = times.getValue(prayer)
}

data class PrayerEvent(val prayer: Prayer, val time: Instant, val date: LocalDate)

/**
 * On-device prayer-time calculation. Everything runs offline; times are UTC
 * instants for the calendar [date] at [Place], which the UI renders in the
 * place's own zone.
 */
object PrayerCalculator {

    /** Nearest-latitude fallback walks toward the equator in these steps until times exist. */
    private const val FALLBACK_STEP_DEGREES = 0.5
    private const val MIN_GAP_SECONDS = 5 * 60

    fun day(place: Place, date: LocalDate, settings: PrayerSettings): DaySchedule {
        val hijri = HijriDate.of(date, settings.hijriOffsetDays)
        val params = adhanParameters(settings, ramadan = hijri.isRamadan)
        val (times, latitude) = solve(place, date, params)

        val rule = resolvedRule(settings.highLatitudeRule, latitude)
        val adjustment = if (latitude == place.latitude) {
            Adjustment.Calculated(rule)
        } else {
            Adjustment.PolarFallback(latitude, rule)
        }
        val fajr = times.fajr.toJava()
        val maghrib = times.maghrib.toJava()
        // Adhan's SunnahTimes recomputes tomorrow at the original latitude and
        // throws after a polar fallback, so the night is measured here instead.
        val tomorrowFajr = solve(place, date.plusDays(1), params).first.fajr.toJava()
        val night = Duration.between(maghrib, tomorrowFajr)

        return DaySchedule(
            place = place,
            date = date,
            times = mapOf(
                Prayer.FAJR to fajr,
                Prayer.SUNRISE to times.sunrise.toJava(),
                Prayer.DHUHR to times.dhuhr.toJava(),
                Prayer.ASR to times.asr.toJava(),
                Prayer.MAGHRIB to maghrib,
                Prayer.ISHA to times.isha.toJava(),
            ),
            imsak = fajr.minusSeconds(settings.imsakMinutesBeforeFajr * 60L),
            middleOfTheNight = roundToMinute(maghrib + night.dividedBy(2)),
            lastThirdOfTheNight = roundToMinute(maghrib + night.multipliedBy(2).dividedBy(3)),
            hijri = hijri,
            method = settings.method,
            adjustment = adjustment,
        )
    }

    /**
     * The next [count] prayer events strictly after [from], in order. Sunrise is
     * included because it ends Fajr; callers that only want adhans filter it out.
     * This is what the notification schedulers consume.
     */
    fun upcoming(place: Place, settings: PrayerSettings, from: Instant, count: Int): List<PrayerEvent> {
        require(count >= 0)
        val events = mutableListOf<PrayerEvent>()
        // Start a day early: in zones far from UTC the previous local day's Isha can still be ahead.
        var date = from.atZone(place.zone).toLocalDate().minusDays(1)
        while (events.size < count) {
            val schedule = day(place, date, settings)
            Prayer.entries
                .map { PrayerEvent(it, schedule[it], date) }
                .filter { it.time.isAfter(from) }
                .forEach { if (events.size < count) events += it }
            date = date.plusDays(1)
        }
        return events
    }

    /** Initial great-circle bearing to the Kaaba, in degrees clockwise from true north. */
    fun qiblaBearing(latitude: Double, longitude: Double): Double =
        Qibla(Coordinates(latitude, longitude)).direction

    internal fun adhanParameters(settings: PrayerSettings, ramadan: Boolean): CalculationParameters {
        val base = when (settings.method) {
            Method.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            Method.ISNA -> CalculationMethod.NORTH_AMERICA.parameters
            Method.MOONSIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters
            Method.EGYPTIAN -> CalculationMethod.EGYPTIAN.parameters
            Method.KARACHI -> CalculationMethod.KARACHI.parameters
            Method.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA.parameters
            Method.DUBAI -> CalculationMethod.DUBAI.parameters
            Method.KUWAIT -> CalculationMethod.KUWAIT.parameters
            Method.QATAR -> CalculationMethod.QATAR.parameters
            Method.SINGAPORE -> CalculationMethod.SINGAPORE.parameters
            Method.TURKEY -> CalculationMethod.TURKEY.parameters
            Method.CUSTOM -> settings.customAngles!!.let {
                CalculationMethod.OTHER.parameters.copy(
                    fajrAngle = it.fajrAngle,
                    ishaAngle = it.ishaAngle,
                    ishaInterval = it.ishaIntervalMinutes,
                )
            }
        }
        val defaultRamadanExtension = if (settings.method == Method.UMM_AL_QURA) 30 else 0
        val ramadanExtension = if (ramadan) settings.ramadanIshaExtensionMinutes ?: defaultRamadanExtension else 0
        val offsets = settings.offsetMinutes
        return base.copy(
            madhab = when (settings.madhab) {
                Madhab.SHAFI -> AdhanMadhab.SHAFI
                Madhab.HANAFI -> AdhanMadhab.HANAFI
            },
            highLatitudeRule = when (settings.highLatitudeRule) {
                HighLatitudeRule.AUTO -> null
                HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> AdhanHighLatitudeRule.MIDDLE_OF_THE_NIGHT
                HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> AdhanHighLatitudeRule.SEVENTH_OF_THE_NIGHT
                HighLatitudeRule.TWILIGHT_ANGLE -> AdhanHighLatitudeRule.TWILIGHT_ANGLE
            },
            shafaq = when (settings.shafaq) {
                Shafaq.GENERAL -> AdhanShafaq.GENERAL
                Shafaq.AHMER -> AdhanShafaq.AHMER
                Shafaq.ABYAD -> AdhanShafaq.ABYAD
            },
            prayerAdjustments = PrayerAdjustments(
                fajr = offsets[Prayer.FAJR] ?: 0,
                sunrise = offsets[Prayer.SUNRISE] ?: 0,
                dhuhr = offsets[Prayer.DHUHR] ?: 0,
                asr = offsets[Prayer.ASR] ?: 0,
                maghrib = offsets[Prayer.MAGHRIB] ?: 0,
                isha = (offsets[Prayer.ISHA] ?: 0) + ramadanExtension,
            ),
        )
    }

    private fun resolvedRule(rule: HighLatitudeRule, latitude: Double): HighLatitudeRule =
        if (rule != HighLatitudeRule.AUTO) {
            rule
        } else {
            when (AdhanHighLatitudeRule.recommendedFor(Coordinates(latitude, 0.0))) {
                AdhanHighLatitudeRule.MIDDLE_OF_THE_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
                AdhanHighLatitudeRule.SEVENTH_OF_THE_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
                AdhanHighLatitudeRule.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
            }
        }

    /**
     * Times for [date], walking toward the equator from the place's latitude
     * until a usable set exists. Returns the times and the latitude used.
     */
    private fun solve(place: Place, date: LocalDate, params: CalculationParameters): Pair<PrayerTimes, Double> {
        val solarDate = solarDate(place, date)
        val components = DateComponents(solarDate.year, solarDate.monthValue, solarDate.dayOfMonth)
        var latitude = place.latitude
        var times = compute(latitude, place.longitude, components, params)
        while (times == null && abs(latitude) > FALLBACK_STEP_DEGREES) {
            latitude -= sign(latitude) * FALLBACK_STEP_DEGREES
            times = compute(latitude, place.longitude, components, params)
        }
        checkNotNull(times) { "No prayer times for ${place.name} on $date" }
        return times to latitude
    }

    /**
     * Null when this latitude has no usable times on this date. Adhan throws when
     * the sun never rises or sets, but near the polar circles the published
     * 0.0.7 (and adhan-swift) can instead return times out of order, e.g. Asr
     * after Maghrib in Tromsø in early January. So we also require every prayer
     * to follow the previous one by at least [MIN_GAP_SECONDS], the same guard
     * upstream adhan-kotlin added after 0.0.7.
     */
    private fun compute(
        latitude: Double,
        longitude: Double,
        date: DateComponents,
        params: CalculationParameters,
    ): PrayerTimes? {
        val times = try {
            PrayerTimes(Coordinates(latitude, longitude), date, params)
        } catch (_: IllegalStateException) {
            return null
        }
        val ordered = listOf(times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha)
            .zipWithNext()
            .all { (a, b) -> (b - a).inWholeSeconds >= MIN_GAP_SECONDS }
        return times.takeIf { ordered }
    }

    /**
     * The date Adhan needs so that its Dhuhr lands on civil [date] in the place's
     * zone. Adhan treats the date as the solar day at that longitude, which
     * matches the civil date except where the zone offset is far from
     * longitude / 15 hours: in Kiritimati (UTC+14, 157°W) or Apia (UTC+13, 171°W)
     * passing the civil date returns the next day's times.
     */
    internal fun solarDate(place: Place, date: LocalDate): LocalDate {
        val localNoon = date.atTime(12, 0).atZone(place.zone).toInstant()
        val longitudeShift = Duration.ofSeconds((place.longitude / 15.0 * 3600).toLong())
        return (localNoon + longitudeShift).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun roundToMinute(instant: Instant): Instant =
        Instant.ofEpochSecond((instant.epochSecond + 30) / 60 * 60)

    private fun kotlin.time.Instant.toJava(): Instant = Instant.ofEpochMilli(toEpochMilliseconds())
}
