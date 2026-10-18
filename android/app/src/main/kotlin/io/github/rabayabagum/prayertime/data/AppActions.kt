package io.github.rabayabagum.prayertime.data

import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.Defaults
import io.github.rabayabagum.prayertime.core.NotificationPreferences
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerAlert
import io.github.rabayabagum.prayertime.core.PrayerSettings

/**
 * State changes, kept pure so they're unit-tested on the JVM. Same rules as the
 * iOS AppModel: the method follows the location's country until the user
 * changes any calculation setting.
 */
object AppActions {

    /** Before the user picks anything: the largest city in the device's zone. */
    fun initial(cities: CityDatabase, deviceZone: String): AppState {
        val city = cities.largest(deviceZone)
            ?: cities.cities.firstOrNull { it.name == "Makkah" }
            ?: cities.cities.first()
        val location = SavedLocation.of(city)
        return AppState(location, Defaults.settingsFor(location.countryCode), false, NotificationPreferences())
    }

    fun countryDefaults(state: AppState): PrayerSettings = Defaults.settingsFor(state.location.countryCode)

    fun setLocation(state: AppState, location: SavedLocation): AppState {
        val moved = state.copy(location = location)
        return if (state.settingsCustomized) moved else moved.copy(settings = countryDefaults(moved))
    }

    fun updateSettings(state: AppState, change: (PrayerSettings) -> PrayerSettings): AppState {
        val settings = change(state.settings)
        return if (settings == state.settings) state else state.copy(settings = settings, settingsCustomized = true)
    }

    fun resetSettings(state: AppState): AppState =
        state.copy(settings = countryDefaults(state), settingsCustomized = false)

    fun setAlertMode(state: AppState, prayer: Prayer, mode: AlertMode): AppState =
        updateAlert(state, prayer) { it.copy(mode = mode) }

    /**
     * Reminders are stored per prayer; the settings screen sets one value for all
     * five prayers (sunrise has no reminder), as on iOS.
     */
    fun setReminderMinutes(state: AppState, minutes: Int): AppState =
        Prayer.entries.filter { it != Prayer.SUNRISE }.fold(state) { s, prayer ->
            updateAlert(s, prayer) { it.copy(reminderMinutesBefore = minutes.takeIf { m -> m > 0 }) }
        }

    fun reminderMinutes(state: AppState): Int = state.notifications.alert(Prayer.FAJR).reminderMinutesBefore ?: 0

    private fun updateAlert(state: AppState, prayer: Prayer, change: (PrayerAlert) -> PrayerAlert): AppState {
        val alerts = state.notifications.alerts + (prayer to change(state.notifications.alert(prayer)))
        return state.copy(notifications = NotificationPreferences(alerts))
    }
}
