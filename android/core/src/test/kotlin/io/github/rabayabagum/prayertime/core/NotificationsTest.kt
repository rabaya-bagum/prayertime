package io.github.rabayabagum.prayertime.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsTest {
    private val toronto = Place("Toronto", 43.6532, -79.3832, "America/Toronto")
    private val inputs = ScheduleInputs(toronto, PrayerSettings(Method.ISNA), NotificationPreferences())
    private val now = ZonedDateTime.of(2026, 5, 15, 14, 0, 0, 0, toronto.zone).toInstant() // after Dhuhr (13:2x EDT)

    @Test
    fun defaultAlertsAreFiveAdhansAndNoSunrise() {
        val alerts = AlertPlanner.alerts(inputs, now, now.plus(Duration.ofDays(2)))
        assertTrue(alerts.all { it.kind == AlertKind.ADHAN && it.sound == PrayerAlert.DEFAULT_SOUND })
        assertTrue(alerts.none { it.prayer == Prayer.SUNRISE })
        assertEquals(10, alerts.size)
        assertEquals(Prayer.ASR, alerts.first().prayer)
        assertEquals(alerts.sortedBy { it.time }, alerts)
    }

    @Test
    fun alertsMatchCalculatedTimes() {
        val day = PrayerCalculator.day(toronto, LocalDate.of(2026, 5, 16), inputs.settings)
        val fajr = AlertPlanner.alerts(inputs, now, now.plus(Duration.ofDays(1))).first { it.prayer == Prayer.FAJR }
        assertEquals(day[Prayer.FAJR], fajr.time)
        assertEquals(LocalDate.of(2026, 5, 16), fajr.date)
    }

    @Test
    fun modesAndRemindersPerPrayer() {
        val prefs = NotificationPreferences(
            NotificationPreferences.DEFAULT_ALERTS + mapOf(
                Prayer.FAJR to PrayerAlert(AlertMode.ADHAN, sound = "madinah", reminderMinutesBefore = 20),
                Prayer.ASR to PrayerAlert(AlertMode.SILENT),
                Prayer.ISHA to PrayerAlert(AlertMode.OFF),
            ),
        )
        val alerts = AlertPlanner.alerts(inputs.copy(notifications = prefs), now, now.plus(Duration.ofHours(24)))
        val byKey = alerts.associateBy { it.kind to it.prayer }
        assertEquals("madinah", byKey.getValue(AlertKind.ADHAN to Prayer.FAJR).sound)
        assertEquals(
            Duration.ofMinutes(20),
            Duration.between(byKey.getValue(AlertKind.REMINDER to Prayer.FAJR).time, byKey.getValue(AlertKind.ADHAN to Prayer.FAJR).time),
        )
        assertEquals(null, byKey.getValue(AlertKind.SILENT to Prayer.ASR).sound)
        assertTrue(alerts.none { it.prayer == Prayer.ISHA })
    }

    @Test
    fun pastAlertsAndRemindersAreDropped() {
        val prefs = NotificationPreferences(
            NotificationPreferences.DEFAULT_ALERTS + (Prayer.ASR to PrayerAlert(AlertMode.ADHAN, reminderMinutesBefore = 30)),
        )
        val asr = PrayerCalculator.day(toronto, LocalDate.of(2026, 5, 15), inputs.settings)[Prayer.ASR]
        val betweenReminderAndAdhan = asr.minus(Duration.ofMinutes(10))
        val alerts = AlertPlanner.alerts(inputs.copy(notifications = prefs), betweenReminderAndAdhan, asr.plus(Duration.ofMinutes(1)))
        assertEquals(listOf(AlertKind.ADHAN), alerts.map { it.kind })
    }

    @Test
    fun iosWindowFillsSixtyThreeSlotsPlusKeepAlive() {
        val plan = NotificationWindowPlanner.plan(inputs, now)
        assertEquals(64, plan.size)
        val keepAlive = plan.single { it.kind == AlertKind.KEEP_ALIVE }
        val lastAdhan = plan.filter { it.kind == AlertKind.ADHAN }.maxOf { it.time }
        assertEquals(lastAdhan.minus(NotificationWindowPlanner.KEEP_ALIVE_LEAD), keepAlive.time)
        // 63 adhans at five a day is about 12.6 days.
        assertTrue(Duration.between(now, lastAdhan).toDays() in 12L..13L)
        assertEquals(plan.map { it.identifier }.toSet().size, plan.size)
    }

    @Test
    fun remindersHalveTheIosWindow() {
        val prefs = NotificationPreferences(
            Prayer.entries.associateWith {
                if (it == Prayer.SUNRISE) PrayerAlert(AlertMode.OFF) else PrayerAlert(AlertMode.ADHAN, reminderMinutesBefore = 15)
            },
        )
        val plan = NotificationWindowPlanner.plan(inputs.copy(notifications = prefs), now)
        val last = plan.filter { it.kind != AlertKind.KEEP_ALIVE }.maxOf { it.time }
        assertTrue(Duration.between(now, last).toDays() in 6L..7L)
    }

    @Test
    fun sparsePreferencesStopAtTheHorizonAndKeepAliveIsNeverInThePast() {
        val onlyFajr = NotificationPreferences(Prayer.entries.associateWith {
            PrayerAlert(if (it == Prayer.FAJR) AlertMode.SILENT else AlertMode.OFF)
        })
        val plan = NotificationWindowPlanner.plan(inputs.copy(notifications = onlyFajr), now)
        assertEquals(31, plan.size) // 30 days of Fajr plus the keep-alive
        assertTrue(plan.all { it.time > now })

        val allOff = NotificationPreferences(Prayer.entries.associateWith { PrayerAlert(AlertMode.OFF) })
        assertEquals(emptyList(), NotificationWindowPlanner.plan(inputs.copy(notifications = allOff), now))
    }

    @Test
    fun diffOnlyTouchesChangedAlerts() {
        val first = NotificationWindowPlanner.plan(inputs, now)
        val pending = first.map { it.identifier }.toSet()

        // A few hours later nothing changed except time passing.
        val later = NotificationWindowPlanner.plan(inputs, now.plus(Duration.ofHours(6)))
        val diff = NotificationWindowPlanner.diff(pending, later)
        assertTrue(diff.add.size <= 4, "added ${diff.add.size}")

        // Changing Maghrib's sound replaces exactly the Maghrib adhans.
        val newSound = inputs.notifications.alerts + (Prayer.MAGHRIB to PrayerAlert(AlertMode.ADHAN, sound = "alaqsa"))
        val changed = NotificationWindowPlanner.plan(inputs.copy(notifications = NotificationPreferences(newSound)), now)
        val soundDiff = NotificationWindowPlanner.diff(pending, changed)
        assertTrue(soundDiff.add.all { it.prayer == Prayer.MAGHRIB })
        assertEquals(soundDiff.add.size, soundDiff.remove.count { it.contains("|maghrib|") })
    }

    @Test
    fun torontoWindowAcrossFallBackDstHasNoGapsOrDuplicates() {
        val beforeFallBack = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, toronto.zone).toInstant()
        val plan = NotificationWindowPlanner.plan(inputs, beforeFallBack).filter { it.kind == AlertKind.ADHAN }
        val perDate = plan.groupingBy { it.date }.eachCount()
        // Full days in the middle of the window have exactly five adhans, including Nov 1.
        assertEquals(5, perDate[LocalDate.of(2026, 11, 1)])
        assertEquals(5, perDate[LocalDate.of(2026, 11, 2)])
    }

    @Test
    fun androidPlanCoversFortyEightHours() {
        val plan = AlarmWindowPlanner.plan(inputs, now)
        assertEquals(10, plan.size)
        assertTrue(plan.all { Duration.between(now, it.time) < AlarmWindowPlanner.HORIZON })
        assertEquals(plan.size, plan.map { it.requestCode }.toSet().size)
    }

    @Test
    fun androidSyncCancelsStaleAndRearmsDesired() {
        val armed = mutableMapOf<Int, PlannedAlert>()
        val client = object : AlarmWindowPlanner.AlarmClient {
            override fun arm(alert: PlannedAlert) { armed[alert.requestCode] = alert }
            override fun cancel(requestCode: Int) { armed.remove(requestCode) }
        }
        val first = AlarmWindowPlanner.plan(inputs, now)
        var codes = AlarmWindowPlanner.sync(first, emptySet(), client)
        assertEquals(first.map { it.requestCode }.toSet(), armed.keys)

        // After Asr fires, the window rolls forward: Asr drops out, a new day's alarm joins.
        val afterAsr = first.first().time.plusSeconds(1)
        val second = AlarmWindowPlanner.plan(inputs, afterAsr)
        codes = AlarmWindowPlanner.sync(second, codes, client)
        assertEquals(codes, armed.keys)
        assertTrue(first.first().requestCode !in armed)

        // Turning everything off cancels everything.
        val off = inputs.copy(notifications = NotificationPreferences(Prayer.entries.associateWith { PrayerAlert(AlertMode.OFF) }))
        codes = AlarmWindowPlanner.sync(AlarmWindowPlanner.plan(off, afterAsr), codes, client)
        assertEquals(emptySet(), codes)
        assertEquals(emptyMap(), armed)
    }

    @Test
    fun requestCodesAreUniqueOverAYear() {
        val prefs = NotificationPreferences(Prayer.entries.associateWith { PrayerAlert(AlertMode.ADHAN, reminderMinutesBefore = 10) })
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val alerts = AlertPlanner.alerts(inputs.copy(notifications = prefs), start, start.plus(Duration.ofDays(366)))
        assertEquals(alerts.size, alerts.map { it.requestCode }.toSet().size)
        assertTrue(alerts.all { it.requestCode > 0 })
    }

    @Test
    fun identifierFormatMatchesIos() {
        val alert = PlannedAlert(
            AlertKind.ADHAN, Instant.ofEpochSecond(1_778_923_200), Prayer.FAJR, LocalDate.of(2026, 5, 16), "makkah",
        )
        assertEquals("adhan|2026-05-16|fajr|1778923200|makkah", alert.identifier)
        assertEquals("keep_alive|-|-|60|-", PlannedAlert(AlertKind.KEEP_ALIVE, Instant.ofEpochSecond(60)).identifier)
    }

    @Test
    fun polarNightStillPlansOrderedAlerts() {
        val tromso = Place("Tromsø", 69.6492, 18.9553, "Europe/Oslo")
        val midwinter = ZonedDateTime.of(2026, 12, 21, 0, 0, 0, 0, tromso.zone).toInstant()
        val all = NotificationPreferences(Prayer.entries.associateWith { PrayerAlert(AlertMode.ADHAN) })
        val plan = NotificationWindowPlanner.plan(ScheduleInputs(tromso, PrayerSettings(Method.MUSLIM_WORLD_LEAGUE), all), midwinter)
        assertEquals(NotificationWindowPlanner.IOS_CAPACITY, plan.size)
        assertEquals(plan.sortedBy { it.time }, plan)
    }
}
