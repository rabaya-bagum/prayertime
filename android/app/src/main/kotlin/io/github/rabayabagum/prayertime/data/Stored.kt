package io.github.rabayabagum.prayertime.data

import io.github.rabayabagum.prayertime.core.AlertMode
import io.github.rabayabagum.prayertime.core.CustomAngles
import io.github.rabayabagum.prayertime.core.HighLatitudeRule
import io.github.rabayabagum.prayertime.core.Madhab
import io.github.rabayabagum.prayertime.core.Method
import io.github.rabayabagum.prayertime.core.NotificationPreferences
import io.github.rabayabagum.prayertime.core.Place
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.PrayerAlert
import io.github.rabayabagum.prayertime.core.PrayerSettings
import io.github.rabayabagum.prayertime.core.Shafaq
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where times are calculated for. The country code picks the default method. */
data class SavedLocation(
    val place: Place,
    val subtitle: String,
    val countryCode: String,
    val isCurrentLocation: Boolean,
) {
    companion object {
        fun of(city: City) = SavedLocation(
            Place(city.name, city.latitude, city.longitude, city.timeZoneId), city.subtitle, city.countryCode, false,
        )

        /**
         * A GPS fix takes the name and IANA zone of the nearest city (SPEC revisions),
         * rounded to ~1 km since nothing finer changes the times.
         */
        fun fix(latitude: Double, longitude: Double, nearest: City): SavedLocation {
            fun round2(x: Double) = Math.round(x * 100) / 100.0
            return SavedLocation(
                Place(nearest.name, round2(latitude), round2(longitude), nearest.timeZoneId),
                nearest.subtitle, nearest.countryCode, true,
            )
        }
    }
}

/** Everything the app persists. Core types stay free of serialization, so these mirror them. */
data class AppState(
    val location: SavedLocation,
    val settings: PrayerSettings,
    /** False until the user changes a calculation setting; until then the method follows the location's country. */
    val settingsCustomized: Boolean,
    val notifications: NotificationPreferences,
)

/**
 * JSON form of [AppState]. Enum names and method ids are stored, never ordinals,
 * so reordering an enum can't corrupt saved settings.
 */
object StoredState {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: AppState): String = json.encodeToString(StateDto.serializer(), StateDto.from(state))

    /** Null when the text is missing or unreadable, so the caller falls back to defaults. */
    fun decode(text: String?): AppState? = text?.let {
        runCatching { json.decodeFromString(StateDto.serializer(), it).toState() }.getOrNull()
    }

    @Serializable
    private data class StateDto(
        val location: LocationDto,
        val settings: SettingsDto,
        val settingsCustomized: Boolean,
        val alerts: Map<String, AlertDto>,
    ) {
        fun toState() = AppState(
            location = location.toLocation(),
            settings = settings.toSettings(),
            settingsCustomized = settingsCustomized,
            notifications = NotificationPreferences(
                NotificationPreferences.DEFAULT_ALERTS + alerts.mapNotNull { (key, alert) ->
                    Prayer.entries.firstOrNull { it.name == key }?.let { it to alert.toAlert() }
                },
            ),
        )

        companion object {
            fun from(state: AppState) = StateDto(
                LocationDto.from(state.location),
                SettingsDto.from(state.settings),
                state.settingsCustomized,
                state.notifications.alerts.entries.associate { (prayer, alert) -> prayer.name to AlertDto.from(alert) },
            )
        }
    }

    @Serializable
    private data class LocationDto(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String,
        val subtitle: String,
        val countryCode: String,
        val isCurrentLocation: Boolean,
    ) {
        fun toLocation() = SavedLocation(Place(name, latitude, longitude, timeZoneId), subtitle, countryCode, isCurrentLocation)

        companion object {
            fun from(l: SavedLocation) = LocationDto(
                l.place.name, l.place.latitude, l.place.longitude, l.place.timeZoneId, l.subtitle, l.countryCode, l.isCurrentLocation,
            )
        }
    }

    @Serializable
    private data class SettingsDto(
        val method: String,
        val madhab: String = Madhab.SHAFI.name,
        val highLatitudeRule: String = HighLatitudeRule.AUTO.name,
        val shafaq: String = Shafaq.GENERAL.name,
        val customFajrAngle: Double? = null,
        val customIshaAngle: Double? = null,
        val customIshaIntervalMinutes: Int = 0,
        val offsetMinutes: Map<String, Int> = emptyMap(),
        val hijriOffsetDays: Int = 0,
        val imsakMinutesBeforeFajr: Int = 10,
        val ramadanIshaExtensionMinutes: Int? = null,
    ) {
        fun toSettings(): PrayerSettings {
            val angles = if (customFajrAngle != null && customIshaAngle != null) {
                CustomAngles(customFajrAngle, customIshaAngle, customIshaIntervalMinutes)
            } else {
                null
            }
            val method = Method.entries.firstOrNull { it.id == method }
                ?.takeIf { it != Method.CUSTOM || angles != null }
                ?: Method.MUSLIM_WORLD_LEAGUE
            return PrayerSettings(
                method = method,
                madhab = enumOr(madhab, Madhab.SHAFI),
                highLatitudeRule = enumOr(highLatitudeRule, HighLatitudeRule.AUTO),
                shafaq = enumOr(shafaq, Shafaq.GENERAL),
                customAngles = angles,
                offsetMinutes = offsetMinutes.mapNotNull { (k, v) -> Prayer.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
                hijriOffsetDays = hijriOffsetDays,
                imsakMinutesBeforeFajr = imsakMinutesBeforeFajr,
                ramadanIshaExtensionMinutes = ramadanIshaExtensionMinutes,
            )
        }

        companion object {
            fun from(s: PrayerSettings) = SettingsDto(
                method = s.method.id,
                madhab = s.madhab.name,
                highLatitudeRule = s.highLatitudeRule.name,
                shafaq = s.shafaq.name,
                customFajrAngle = s.customAngles?.fajrAngle,
                customIshaAngle = s.customAngles?.ishaAngle,
                customIshaIntervalMinutes = s.customAngles?.ishaIntervalMinutes ?: 0,
                offsetMinutes = s.offsetMinutes.entries.associate { (p, m) -> p.name to m },
                hijriOffsetDays = s.hijriOffsetDays,
                imsakMinutesBeforeFajr = s.imsakMinutesBeforeFajr,
                ramadanIshaExtensionMinutes = s.ramadanIshaExtensionMinutes,
            )
        }
    }

    @Serializable
    private data class AlertDto(
        val mode: String,
        val sound: String = PrayerAlert.DEFAULT_SOUND,
        val reminderMinutesBefore: Int? = null,
    ) {
        fun toAlert() = PrayerAlert(enumOr(mode, AlertMode.ADHAN), sound, reminderMinutesBefore)

        companion object {
            fun from(a: PrayerAlert) = AlertDto(a.mode.name, a.sound, a.reminderMinutesBefore)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
