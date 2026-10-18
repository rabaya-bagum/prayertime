package io.github.rabayabagum.prayertime.data

import io.github.rabayabagum.prayertime.core.Method
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerCalculator
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors the iOS WidgetTimelineTests. */
class WidgetContentTest {
    private val db = File(System.getProperty("cities.tsv")).reader().use(::CityDatabase)
    private val state = AppActions.updateSettings(AppActions.initial(db, "America/Toronto")) { it.copy(method = Method.ISNA) }
    private val zone = state.location.place.zone
    private val today = PrayerCalculator.day(state.location.place, LocalDate.of(2026, 5, 15), state.settings)

    @Test
    fun afternoonShowsAsrNextAndRefreshesAtAsr() {
        val now = ZonedDateTime.of(2026, 5, 15, 14, 0, 0, 0, zone).toInstant()
        val content = WidgetContent.of(state, now)
        assertEquals(Prayer.ASR, content.next.prayer)
        assertEquals(today[Prayer.ASR], content.refreshAt)
        assertEquals(LocalDate.of(2026, 5, 15), content.day)
        assertEquals(6, content.times.size)
        assertTrue(content.nextIsToday)
    }

    @Test
    fun atAsrItselfTheWidgetAlreadyShowsMaghrib() {
        val content = WidgetContent.of(state, today[Prayer.ASR])
        assertEquals(Prayer.MAGHRIB, content.next.prayer)
    }

    @Test
    fun afterIshaItRefreshesAtMidnightWithTomorrowsFajrNext() {
        val now = today[Prayer.ISHA].plusSeconds(60)
        val content = WidgetContent.of(state, now)
        assertEquals(Prayer.FAJR, content.next.prayer)
        assertFalse(content.nextIsToday, "Fajr is tomorrow; today's list highlights nothing")
        assertEquals(ZonedDateTime.of(2026, 5, 16, 0, 0, 0, 0, zone).toInstant(), content.refreshAt)
    }

    @Test
    fun theDstChangeDayRollsOverAtLocalMidnight() {
        val now = ZonedDateTime.of(2026, 10, 31, 23, 0, 0, 0, zone).toInstant()
        assertEquals(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone).toInstant(), WidgetContent.of(state, now).refreshAt)
    }
}
