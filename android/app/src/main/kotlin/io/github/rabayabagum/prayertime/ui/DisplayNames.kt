package io.github.rabayabagum.prayertime.ui

import io.github.rabayabagum.prayertime.core.Adjustment
import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.HighLatitudeRule
import io.github.rabayabagum.prayertime.core.HijriDate
import io.github.rabayabagum.prayertime.core.Madhab
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.Shafaq
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// English names, matching the iOS app's DisplayNames.swift. Move to string
// resources when the app is localized.

val Prayer.displayName: String
    get() = when (this) {
        Prayer.FAJR -> "Fajr"
        Prayer.SUNRISE -> "Sunrise"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
    }

val AlertMode.displayName: String
    get() = when (this) {
        AlertMode.ADHAN -> "Adhan"
        AlertMode.SILENT -> "Silent"
        AlertMode.OFF -> "Off"
    }

val Madhab.displayName: String
    get() = when (this) {
        Madhab.SHAFI -> "Standard (Shafi'i, Maliki, Hanbali)"
        Madhab.HANAFI -> "Hanafi"
    }

val HighLatitudeRule.displayName: String
    get() = when (this) {
        HighLatitudeRule.AUTO -> "Automatic"
        HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> "Middle of the night"
        HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> "Seventh of the night"
        HighLatitudeRule.TWILIGHT_ANGLE -> "Twilight angle"
    }

val Shafaq.displayName: String
    get() = when (this) {
        Shafaq.GENERAL -> "General"
        Shafaq.AHMER -> "Red (ahmer)"
        Shafaq.ABYAD -> "White (abyad)"
    }

val Adjustment.displayName: String
    get() = when (this) {
        is Adjustment.Calculated -> highLatitudeRule.displayName
        is Adjustment.PolarFallback -> "${highLatitudeRule.displayName}, computed at ${"%.1f".format(latitudeUsed)}°"
    }

private val hijriMonths = listOf(
    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
    "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
)

val HijriDate.formatted: String get() = "$day ${hijriMonths[month - 1]} $year AH"

/** Times always show on the place's clock, which may differ from the device's zone. */
fun Instant.clockTime(zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(atZone(zone))

fun Instant.longDate(zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(atZone(zone))
