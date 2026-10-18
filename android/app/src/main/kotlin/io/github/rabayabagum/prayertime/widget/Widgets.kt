package io.github.rabayabagum.prayertime.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import io.github.rabayabagum.prayertime.PrayerTimeApplication
import io.github.rabayabagum.prayertime.R
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.data.WidgetContent
import io.github.rabayabagum.prayertime.ui.clockTime
import io.github.rabayabagum.prayertime.ui.displayName
import java.time.Instant
import kotlin.concurrent.thread

/** The next prayer with a self-running countdown (2×2). */
class NextPrayerWidgetProvider : PrayerWidgetProvider()

/** The day's six times with the next one highlighted (3×3). */
class TodayWidgetProvider : PrayerWidgetProvider()

/**
 * Both widgets redraw together through [WidgetUpdater]: when the launcher asks,
 * at the scheduled refresh (next prayer or midnight), and on clock or zone changes.
 */
abstract class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.updateAsync(context, this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                WidgetUpdater.updateAsync(context, this)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        // The last widget of this kind was removed; stop refreshing if none of the other is left either.
        WidgetUpdater.updateAsync(context, this)
    }
}

object WidgetUpdater {
    const val ACTION_REFRESH = "io.github.rabayabagum.prayertime.WIDGET_REFRESH"

    private val rows = mapOf(
        Prayer.FAJR to Triple(R.id.row_fajr, R.id.name_fajr, R.id.time_fajr),
        Prayer.SUNRISE to Triple(R.id.row_sunrise, R.id.name_sunrise, R.id.time_sunrise),
        Prayer.DHUHR to Triple(R.id.row_dhuhr, R.id.name_dhuhr, R.id.time_dhuhr),
        Prayer.ASR to Triple(R.id.row_asr, R.id.name_asr, R.id.time_asr),
        Prayer.MAGHRIB to Triple(R.id.row_maghrib, R.id.name_maghrib, R.id.time_maghrib),
        Prayer.ISHA to Triple(R.id.row_isha, R.id.name_isha, R.id.time_isha),
    )

    /** From a receiver: draws off the main thread and keeps the receiver alive until done. */
    fun updateAsync(context: Context, receiver: AppWidgetProvider) {
        val pending = receiver.goAsync()
        thread(name = "widget-update") {
            try {
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    /** Redraws every placed widget and schedules the next redraw. Call off the main thread. */
    fun update(context: Context, now: Instant = Instant.now()) {
        val manager = AppWidgetManager.getInstance(context)
        val nextIds = manager.getAppWidgetIds(ComponentName(context, NextPrayerWidgetProvider::class.java))
        val todayIds = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (nextIds.isEmpty() && todayIds.isEmpty()) {
            cancelRefresh(context)
            return
        }

        val app = context as PrayerTimeApplication
        val content = WidgetContent.of(app.loadState(), now)
        val open = app.prayerScheduleSource.openAppIntent(context)
        if (nextIds.isNotEmpty()) manager.updateAppWidget(nextIds, nextPrayerViews(context, content, now, open))
        if (todayIds.isNotEmpty()) manager.updateAppWidget(todayIds, todayViews(context, content, now, open))
        scheduleRefresh(context, content.refreshAt)
    }

    private fun nextPrayerViews(context: Context, content: WidgetContent, now: Instant, open: PendingIntent) =
        RemoteViews(context.packageName, R.layout.widget_next_prayer).apply {
            setTextViewText(R.id.place, content.placeName)
            setTextViewText(R.id.prayer, content.next.prayer.displayName)
            setTextViewText(R.id.time, content.next.time.clockTime(content.zone))
            countdown(R.id.countdown, content.next.time, now)
            setOnClickPendingIntent(R.id.widget_root, open)
            setContentDescription(
                R.id.widget_root,
                "Next prayer ${content.next.prayer.displayName} at ${content.next.time.clockTime(content.zone)} in ${content.placeName}",
            )
        }

    private fun todayViews(context: Context, content: WidgetContent, now: Instant, open: PendingIntent) =
        RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.place, content.placeName)
            countdown(R.id.countdown, content.next.time, now)
            val text = context.getColor(R.color.widget_text)
            val secondary = context.getColor(R.color.widget_secondary)
            val accent = context.getColor(R.color.widget_accent)
            for ((prayer, ids) in rows) {
                val (row, name, time) = ids
                val isNext = content.nextIsToday && content.next.prayer == prayer
                val color = when {
                    isNext -> accent
                    prayer == Prayer.SUNRISE -> secondary
                    else -> text
                }
                setTextViewText(name, prayer.displayName)
                setTextViewText(time, content.times.getValue(prayer).clockTime(content.zone))
                setTextColor(name, color)
                setTextColor(time, color)
                setInt(row, "setBackgroundResource", if (isNext) R.drawable.widget_row_highlight else 0)
            }
            setOnClickPendingIntent(R.id.widget_root, open)
        }

    /** A Chronometer counting down to [target]; it ticks on its own between redraws. */
    private fun RemoteViews.countdown(id: Int, target: Instant, now: Instant) {
        val remaining = target.toEpochMilli() - now.toEpochMilli()
        setChronometer(id, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(id, true)
    }

    /**
     * Redraw at the next prayer or midnight. A one-minute window alarm needs no
     * exact-alarm permission, and RTC (not RTC_WAKEUP) doesn't wake the phone: with
     * the screen off it fires as soon as the phone wakes, when the widget is next seen.
     */
    private fun scheduleRefresh(context: Context, at: Instant) {
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, at.toEpochMilli(), 60_000, refreshIntent(context))
    }

    private fun cancelRefresh(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, NextPrayerWidgetProvider::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
