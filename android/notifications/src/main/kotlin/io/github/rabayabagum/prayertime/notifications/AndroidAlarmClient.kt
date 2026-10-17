package io.github.rabayabagum.prayertime.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.rabayabagum.prayertime.core.AlarmWindowPlanner
import io.github.rabayabagum.prayertime.core.AlertKind
import io.github.rabayabagum.prayertime.core.PlannedAlert

/**
 * Arms one AlarmManager alarm per planned alert.
 *
 * - Adhans use setAlarmClock: never deferred by Doze, and the app may start the
 *   playback foreground service when it fires.
 * - Silent alerts and reminders use setExactAndAllowWhileIdle.
 * - Without the exact-alarm permission everything falls back to setWindow, and
 *   the receiver posts a notification with the short clip instead of starting
 *   the service (inexact alarms don't allow a background service start).
 */
internal class AndroidAlarmClient(
    private val context: Context,
    private val timeZoneId: String?,
) : AlarmWindowPlanner.AlarmClient {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun arm(alert: PlannedAlert) {
        val at = alert.time.toEpochMilli()
        val exact = PrayerScheduler.canScheduleExactAlarms(context)
        val operation = pendingIntent(alert.requestCode, AlertReceiver.intent(context, alert, exact, timeZoneId))
        try {
            when {
                !exact -> alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, operation)
                alert.kind == AlertKind.ADHAN -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, PrayerScheduler.source(context).openAppIntent(context)),
                    operation,
                )
                else -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
            val fallback = pendingIntent(alert.requestCode, AlertReceiver.intent(context, alert, false, timeZoneId))
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, fallback)
        }
    }

    override fun cancel(requestCode: Int) {
        // PendingIntent identity ignores extras, so a bare intent with the same action matches.
        alarmManager.cancel(pendingIntent(requestCode, AlertReceiver.baseIntent(context)))
    }

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val INEXACT_WINDOW_MS = 5 * 60 * 1000L
    }
}
