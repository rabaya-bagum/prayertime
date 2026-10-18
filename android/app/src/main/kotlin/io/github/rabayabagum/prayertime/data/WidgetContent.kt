package io.github.rabayabagum.prayertime.data

import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerCalculator
import io.github.rabayabagum.prayertime.core.PrayerEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the home-screen widgets show at [now], and when they must redraw: at the
 * next prayer (so it switches to the one after) or the place's midnight (so
 * "today" rolls over), whichever comes first. Same boundaries as the iOS
 * WidgetTimeline. Countdowns run on their own between refreshes.
 */
data class WidgetContent(
    val placeName: String,
    val subtitle: String,
    val zone: ZoneId,
    val day: LocalDate,
    val times: Map<Prayer, Instant>,
    /** The next prayer (or sunrise) strictly after [now]. */
    val next: PrayerEvent,
    val refreshAt: Instant,
) {
    /** Whether [next] falls on [day], so the "today" list can highlight it. */
    val nextIsToday: Boolean get() = next.date == day

    companion object {
        fun of(state: AppState, now: Instant): WidgetContent {
            val place = state.location.place
            val day = now.atZone(place.zone).toLocalDate()
            val schedule = PrayerCalculator.day(place, day, state.settings)
            val next = PrayerCalculator.upcoming(place, state.settings, now, 1).first()
            val midnight = day.plusDays(1).atStartOfDay(place.zone).toInstant()
            return WidgetContent(
                placeName = place.name,
                subtitle = state.location.subtitle,
                zone = place.zone,
                day = day,
                times = schedule.times,
                next = next,
                refreshAt = minOf(next.time, midnight),
            )
        }
    }
}
