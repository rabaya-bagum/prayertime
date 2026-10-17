package io.github.rabayabagum.prayertime.core

import java.time.ZoneId

/** The five daily prayers plus sunrise, which ends the Fajr window. */
enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/**
 * Calculation conventions offered to the user. Every preset maps to an Adhan
 * method; CUSTOM uses the angles in [PrayerSettings.customAngles].
 *
 * Only methods present in both adhan-swift and adhan-kotlin are listed, so both
 * apps offer the same choices. (Tehran needs a Maghrib angle, which only
 * adhan-swift supports.) The ids are stored in settings and shared test data,
 * so never rename them.
 */
enum class Method(val id: String, val displayName: String) {
    MUSLIM_WORLD_LEAGUE("mwl", "Muslim World League"),
    ISNA("isna", "ISNA (North America)"),
    MOONSIGHTING_COMMITTEE("moonsighting", "Moonsighting Committee"),
    EGYPTIAN("egyptian", "Egyptian General Authority of Survey"),
    KARACHI("karachi", "University of Islamic Sciences, Karachi"),
    UMM_AL_QURA("ummalqura", "Umm al-Qura, Makkah"),
    DUBAI("dubai", "Dubai"),
    KUWAIT("kuwait", "Kuwait"),
    QATAR("qatar", "Qatar"),
    SINGAPORE("singapore", "Singapore (MUIS)"),
    TURKEY("turkey", "Diyanet, Turkey"),
    CUSTOM("custom", "Custom angles");

    companion object {
        fun fromId(id: String): Method = entries.first { it.id == id }
    }
}

enum class Madhab { SHAFI, HANAFI }

/** AUTO follows Adhan's recommendation: seventh of the night above 48°, middle of the night below. */
enum class HighLatitudeRule { AUTO, MIDDLE_OF_THE_NIGHT, SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE }

/** Twilight used by the Moonsighting Committee method for Isha. Ignored by other methods. */
enum class Shafaq { GENERAL, AHMER, ABYAD }

/** Fajr/Isha angles for [Method.CUSTOM]. A positive [ishaIntervalMinutes] replaces the Isha angle. */
data class CustomAngles(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
) {
    init {
        require(fajrAngle in 0.0..30.0) { "Fajr angle out of range: $fajrAngle" }
        require(ishaAngle in 0.0..30.0) { "Isha angle out of range: $ishaAngle" }
        require(ishaIntervalMinutes in 0..240) { "Isha interval out of range: $ishaIntervalMinutes" }
    }
}

data class PrayerSettings(
    val method: Method,
    val madhab: Madhab = Madhab.SHAFI,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.AUTO,
    val shafaq: Shafaq = Shafaq.GENERAL,
    val customAngles: CustomAngles? = null,
    /** User's per-prayer ± minutes, applied on top of the method's own adjustments. */
    val offsetMinutes: Map<Prayer, Int> = emptyMap(),
    /** Shifts the Umm al-Qura Hijri date to match local moon sighting. */
    val hijriOffsetDays: Int = 0,
    /** Imsak is a convention, not a ruling, so the gap before Fajr is configurable. */
    val imsakMinutesBeforeFajr: Int = 10,
    /**
     * Minutes added to Isha during Ramadan. Null means the method's own rule:
     * +30 for Umm al-Qura (Isha = Maghrib + 120), 0 otherwise.
     */
    val ramadanIshaExtensionMinutes: Int? = null,
) {
    init {
        require(method != Method.CUSTOM || customAngles != null) { "CUSTOM method needs customAngles" }
        require(hijriOffsetDays in -2..2) { "Hijri offset must be within ±2 days" }
        require(imsakMinutesBeforeFajr in 0..60) { "Imsak gap out of range" }
        require(offsetMinutes.values.all { it in -60..60 }) { "Prayer offsets must be within ±60 minutes" }
    }
}

/**
 * A location the user picked or that GPS resolved to. The IANA zone is stored
 * with it, never a fixed offset, so DST and cities in other zones resolve correctly.
 */
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
) {
    val zone: ZoneId = ZoneId.of(timeZoneId)

    init {
        require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
    }
}
