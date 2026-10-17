package io.github.rabayabagum.prayertime.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What happens at a prayer's start time. */
enum class AlertMode {
    /** Adhan audio: a short clip on iOS, the full adhan via a foreground service on Android. */
    ADHAN,

    /** A notification with no sound. */
    SILENT,

    OFF,
}

data class PrayerAlert(
    val mode: AlertMode,
    /** Reciter/sound id. The apps map it to bundled audio files. */
    val sound: String = DEFAULT_SOUND,
    /** A separate heads-up this many minutes before the prayer, or null for none. */
    val reminderMinutesBefore: Int? = null,
) {
    init {
        require(reminderMinutesBefore == null || reminderMinutesBefore in 1..120) { "Reminder must be 1–120 minutes" }
        require(sound.matches(Regex("[a-z0-9_]+"))) { "Sound ids are lowercase resource names: $sound" }
    }

    companion object {
        const val DEFAULT_SOUND = "azeez"
    }
}

data class NotificationPreferences(
    val alerts: Map<Prayer, PrayerAlert> = DEFAULT_ALERTS,
) {
    fun alert(prayer: Prayer): PrayerAlert = alerts[prayer] ?: DEFAULT_ALERTS.getValue(prayer)

    companion object {
        /** Adhan for the five prayers. Sunrise only ends Fajr, so it is off unless the user opts in. */
        val DEFAULT_ALERTS: Map<Prayer, PrayerAlert> = Prayer.entries.associateWith {
            if (it == Prayer.SUNRISE) PrayerAlert(AlertMode.OFF) else PrayerAlert(AlertMode.ADHAN)
        }
    }
}

/** Everything a scheduler needs, as stored by the app. */
data class ScheduleInputs(
    val place: Place,
    val settings: PrayerSettings,
    val notifications: NotificationPreferences,
)

enum class AlertKind { ADHAN, SILENT, REMINDER, KEEP_ALIVE }

/**
 * One notification or alarm to arm. [identifier] encodes everything that
 * matters (kind, prayer, date, instant, sound), so two plans can be diffed by
 * identifier alone: if a setting changes an alert, its identifier changes too.
 */
data class PlannedAlert(
    val kind: AlertKind,
    val time: Instant,
    val prayer: Prayer? = null,
    val date: LocalDate? = null,
    val sound: String? = null,
    /** When the prayer itself begins; differs from [time] for reminders. Not part of [identifier]. */
    val prayerTime: Instant? = null,
) {
    val identifier: String =
        listOf(kind.name.lowercase(), date ?: "-", prayer?.name?.lowercase() ?: "-", time.epochSecond, sound ?: "-")
            .joinToString("|")

    /**
     * Stable int for Android PendingIntent request codes: unique per
     * (date, prayer, kind), so re-arming the same alert replaces the old alarm.
     */
    val requestCode: Int
        get() {
            val day = date?.toEpochDay() ?: time.epochSecond / 86_400
            return (day * 32 + (prayer?.ordinal ?: 7) * 4 + kind.ordinal).toInt()
        }
}

/** Turns prayer times and preferences into alerts. Pure, so both platforms' adapters stay thin. */
object AlertPlanner {

    /** Alerts strictly after [now] whose time falls before [until], in time order. */
    fun alerts(inputs: ScheduleInputs, now: Instant, until: Instant): List<PlannedAlert> {
        val alerts = mutableListOf<PlannedAlert>()
        val zone = inputs.place.zone
        // A day early for zones far from UTC, a day late for reminders crossing midnight.
        var date = now.atZone(zone).toLocalDate().minusDays(1)
        val lastDate = until.atZone(zone).toLocalDate().plusDays(1)
        while (!date.isAfter(lastDate)) {
            val day = PrayerCalculator.day(inputs.place, date, inputs.settings)
            for (prayer in Prayer.entries) {
                val alert = inputs.notifications.alert(prayer)
                if (alert.mode == AlertMode.OFF) continue
                val time = day[prayer]
                alerts += PlannedAlert(
                    kind = if (alert.mode == AlertMode.ADHAN) AlertKind.ADHAN else AlertKind.SILENT,
                    time = time,
                    prayer = prayer,
                    date = date,
                    sound = alert.sound.takeIf { alert.mode == AlertMode.ADHAN },
                    prayerTime = time,
                )
                alert.reminderMinutesBefore?.let { minutes ->
                    alerts += PlannedAlert(
                        AlertKind.REMINDER, time.minus(Duration.ofMinutes(minutes.toLong())), prayer, date, prayerTime = time,
                    )
                }
            }
            date = date.plusDays(1)
        }
        return alerts.filter { it.time.isAfter(now) && it.time.isBefore(until) }.sortedBy { it.time }
    }
}

/**
 * iOS keeps at most 64 pending local notifications per app. The plan fills
 * [capacity] - 1 slots in time order and spends the last on a keep-alive
 * notification a day before the window runs out ("open the app to keep adhans
 * scheduled"), which catches users who never open the app or force-quit it.
 *
 * The iOS adapter calls this on every launch, background refresh, significant
 * time change and time-zone change, then applies [diff].
 */
object NotificationWindowPlanner {
    const val IOS_CAPACITY = 64
    const val HORIZON_DAYS = 30L

    /** The keep-alive goes off this long before the last planned alert. */
    val KEEP_ALIVE_LEAD: Duration = Duration.ofHours(24)

    fun plan(inputs: ScheduleInputs, now: Instant, capacity: Int = IOS_CAPACITY): List<PlannedAlert> {
        require(capacity >= 2)
        val alerts = AlertPlanner.alerts(inputs, now, now.plus(Duration.ofDays(HORIZON_DAYS))).take(capacity - 1)
        val last = alerts.lastOrNull() ?: return emptyList()
        val keepAliveTime = maxOf(last.time.minus(KEEP_ALIVE_LEAD), now.plus(Duration.ofHours(1)))
        // The keep-alive carries the last alert's instant so its text can say "scheduled until …".
        val keepAlive = PlannedAlert(AlertKind.KEEP_ALIVE, keepAliveTime, prayerTime = last.time)
        return (alerts + keepAlive).sortedBy { it.time }
    }

    data class Diff(val remove: Set<String>, val add: List<PlannedAlert>)

    /** What to remove from and add to the pending set so it equals [desired]. */
    fun diff(pendingIdentifiers: Set<String>, desired: List<PlannedAlert>): Diff {
        val desiredIds = desired.map { it.identifier }.toSet()
        return Diff(
            remove = pendingIdentifiers - desiredIds,
            add = desired.filter { it.identifier !in pendingIdentifiers },
        )
    }
}

/**
 * Android arms real alarms for a rolling [HORIZON] rather than only the next
 * prayer: if one re-arm is lost (process killed mid-handler, OEM battery
 * killer), the following prayers still fire. Every alarm firing, boot, time
 * change and time-zone change re-runs [sync], so the window keeps rolling.
 */
object AlarmWindowPlanner {
    val HORIZON: Duration = Duration.ofHours(48)

    fun plan(inputs: ScheduleInputs, now: Instant): List<PlannedAlert> =
        AlertPlanner.alerts(inputs, now, now.plus(HORIZON))

    /** The platform side: AlarmManager on Android, a fake in tests. */
    interface AlarmClient {
        fun arm(alert: PlannedAlert)
        fun cancel(requestCode: Int)
    }

    /**
     * Cancels alarms armed last time that are no longer wanted, then (re)arms
     * every desired alarm. AlarmManager can't list pending alarms, so the
     * caller persists the returned request codes and passes them back next time.
     */
    fun sync(desired: List<PlannedAlert>, previouslyArmed: Set<Int>, client: AlarmClient): Set<Int> {
        val desiredCodes = desired.map { it.requestCode }.toSet()
        (previouslyArmed - desiredCodes).forEach(client::cancel)
        desired.forEach(client::arm)
        return desiredCodes
    }
}
