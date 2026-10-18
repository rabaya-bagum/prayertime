package io.github.rabayabagum.prayertime.data

import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.CustomAngles
import io.github.rabayabagum.prayertime.core.HighLatitudeRule
import io.github.rabayabagum.prayertime.core.Madhab
import io.github.rabayabagum.prayertime.core.Method
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerAlert
import io.github.rabayabagum.prayertime.core.Shafaq
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors the iOS AppModelTests, plus the JSON format the app persists. */
class AppStateTest {
    private val db = File(System.getProperty("cities.tsv")).reader().use(::CityDatabase)

    private fun city(query: String) = SavedLocation.of(db.search(query).first())

    @Test
    fun methodFollowsCountryUntilCustomized() {
        var state = AppActions.initial(db, "America/Toronto")
        state = AppActions.setLocation(state, city("Karachi, PK"))
        assertEquals(Method.KARACHI, state.settings.method)
        assertEquals(Madhab.HANAFI, state.settings.madhab)

        state = AppActions.setLocation(state, city("Toronto, CA"))
        assertEquals(Method.MOONSIGHTING_COMMITTEE, state.settings.method)
        assertEquals(Madhab.SHAFI, state.settings.madhab)

        state = AppActions.updateSettings(state) { it.copy(method = Method.ISNA) }
        state = AppActions.setLocation(state, city("Dubai, AE"))
        assertEquals(Method.ISNA, state.settings.method, "a customized method survives a move")

        state = AppActions.resetSettings(state)
        assertEquals(Method.DUBAI, state.settings.method)
        assertFalse(state.settingsCustomized)
    }

    @Test
    fun anUnchangedEditDoesNotMarkSettingsCustomized() {
        val state = AppActions.initial(db, "America/Toronto")
        assertEquals(state, AppActions.updateSettings(state) { it })
    }

    @Test
    fun firstLaunchPicksTheLargestCityInTheDeviceZone() {
        val state = AppActions.initial(db, "Europe/Oslo")
        assertEquals("Oslo", state.location.place.name)
        assertFalse(state.location.isCurrentLocation)
        assertEquals(AppActions.initial(db, "Nowhere/Unknown").location.place.name, "Makkah")
    }

    @Test
    fun oneReminderValueAppliesToTheFivePrayers() {
        var state = AppActions.initial(db, "America/Toronto")
        state = AppActions.setReminderMinutes(state, 15)
        assertEquals(15, AppActions.reminderMinutes(state))
        assertTrue(Prayer.entries.filter { it != Prayer.SUNRISE }.all { state.notifications.alert(it).reminderMinutesBefore == 15 })
        assertNull(state.notifications.alert(Prayer.SUNRISE).reminderMinutesBefore)

        state = AppActions.setReminderMinutes(state, 0)
        assertTrue(Prayer.entries.all { state.notifications.alert(it).reminderMinutesBefore == null })
    }

    @Test
    fun alertModeChangesKeepTheReminder() {
        var state = AppActions.setReminderMinutes(AppActions.initial(db, "America/Toronto"), 10)
        state = AppActions.setAlertMode(state, Prayer.ISHA, AlertMode.SILENT)
        assertEquals(PrayerAlert(AlertMode.SILENT, reminderMinutesBefore = 10), state.notifications.alert(Prayer.ISHA))
    }

    @Test
    fun gpsFixIsRoundedToAboutAKilometre() {
        val fix = SavedLocation.fix(43.65432, -79.38765, db.nearest(43.65, -79.38)!!)
        assertEquals(43.65, fix.place.latitude)
        assertEquals(-79.39, fix.place.longitude)
        assertEquals("America/Toronto", fix.place.timeZoneId)
        assertTrue(fix.isCurrentLocation)
    }

    @Test
    fun everythingRoundTripsThroughJson() {
        var state = AppActions.setLocation(AppActions.initial(db, "America/Toronto"), city("Oslo"))
        state = AppActions.updateSettings(state) {
            it.copy(
                method = Method.CUSTOM,
                customAngles = CustomAngles(15.5, 14.0, 90),
                madhab = Madhab.HANAFI,
                highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE,
                shafaq = Shafaq.ABYAD,
                offsetMinutes = mapOf(Prayer.FAJR to -5, Prayer.ISHA to 3),
                hijriOffsetDays = 1,
                imsakMinutesBeforeFajr = 15,
            )
        }
        state = AppActions.setAlertMode(AppActions.setReminderMinutes(state, 20), Prayer.SUNRISE, AlertMode.SILENT)

        assertEquals(state, StoredState.decode(StoredState.encode(state)))
    }

    @Test
    fun unreadableOrPartialJsonFallsBackSafely() {
        assertNull(StoredState.decode(null))
        assertNull(StoredState.decode("not json"))
        // An unknown method id or enum name from a newer version falls back instead of crashing.
        val text = StoredState.encode(AppActions.initial(db, "America/Toronto"))
            .replace("\"moonsighting\"", "\"jakim\"")
            .replace("\"SHAFI\"", "\"MALIKI\"")
        val decoded = StoredState.decode(text)!!
        assertEquals(Method.MUSLIM_WORLD_LEAGUE, decoded.settings.method)
        assertEquals(Madhab.SHAFI, decoded.settings.madhab)
    }
}
