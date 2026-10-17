package io.github.rabayabagum.prayertime.core

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Hijri date from the platform's Umm al-Qura calendar (java.time's
 * HijrahChronology is Umm al-Qura based, like iOS `.islamicUmmAlQura`), shifted
 * by the user's ±2-day offset. Months that begin by moon sighting can't be
 * computed in advance, which is why the offset exists.
 */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val isRamadan: Boolean get() = month == RAMADAN

    companion object {
        const val RAMADAN = 9

        fun of(date: LocalDate, offsetDays: Int = 0): HijriDate {
            val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
            return HijriDate(
                year = h.get(ChronoField.YEAR),
                month = h.get(ChronoField.MONTH_OF_YEAR),
                day = h.get(ChronoField.DAY_OF_MONTH),
            )
        }
    }
}
