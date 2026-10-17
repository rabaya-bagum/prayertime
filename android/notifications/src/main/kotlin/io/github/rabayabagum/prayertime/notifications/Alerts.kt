package io.github.rabayabagum.prayertime.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import io.github.rabayabagum.prayertime.core.Prayer
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Notification channels. A channel's sound can't change after creation, so
 * each adhan clip gets its own channel ("adhan_clip_<sound>"); changing the
 * reciter switches channel instead of editing one.
 */
internal object Channels {
    const val PLAYING = "adhan_playing"
    const val SILENT = "prayer_silent"
    const val REMINDER = "prayer_reminder"

    fun clip(sound: String) = "adhan_clip_$sound"

    fun ensure(context: Context, source: PrayerScheduleSource, sounds: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = mutableListOf(
            NotificationChannel(PLAYING, context.getString(R.string.channel_adhan_playing), NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) },
            NotificationChannel(SILENT, context.getString(R.string.channel_silent), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null) },
            NotificationChannel(REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (sound in sounds) {
            val uri = source.adhanClipUri(sound) ?: continue
            channels += NotificationChannel(
                clip(sound), context.getString(R.string.channel_adhan_clip, sound), NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(uri, attributes) }
        }
        // Creating an existing channel only updates its name, so this is safe to repeat.
        manager.createNotificationChannels(channels)
    }
}

/** Posting the notifications for fired alerts. */
internal object Alerts {

    fun postAdhanClip(context: Context, alert: Fired) {
        val name = prayerName(context, alert.prayer)
        val channel = alert.sound?.let(Channels::clip)
            ?.takeIf { context.getSystemService(NotificationManager::class.java).getNotificationChannel(it) != null }
            ?: Channels.REMINDER
        post(context, alert.notificationId, channel, name, timeFor(context, alert))
    }

    fun postSilent(context: Context, alert: Fired) =
        post(context, alert.notificationId, Channels.SILENT, prayerName(context, alert.prayer), timeFor(context, alert))

    fun postBegan(context: Context, alert: Fired) {
        val name = prayerName(context, alert.prayer)
        post(
            context, alert.notificationId, Channels.SILENT, name,
            context.getString(R.string.alert_began_at, name, clock(alert)),
        )
    }

    fun postReminder(context: Context, alert: Fired) {
        val name = prayerName(context, alert.prayer)
        post(
            context, alert.notificationId, Channels.REMINDER, name,
            context.getString(R.string.alert_reminder, name, clock(alert)),
        )
    }

    fun playing(context: Context, alert: Fired, stop: android.app.PendingIntent): Notification =
        builder(context, Channels.PLAYING, prayerName(context, alert.prayer), timeFor(context, alert))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.alert_stop), stop).build())
            .setDeleteIntent(stop)
            .build()

    private fun post(context: Context, id: Int, channel: String, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Without POST_NOTIFICATIONS (Android 13+) this is silently dropped; onboarding asks for it.
        if (!manager.areNotificationsEnabled()) return
        manager.notify(id, builder(context, channel, title, text).setAutoCancel(true).build())
    }

    private fun builder(context: Context, channel: String, title: String, text: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(PrayerScheduler.source(context).openAppIntent(context))

    private fun timeFor(context: Context, alert: Fired): String =
        if (alert.prayer == Prayer.SUNRISE) {
            context.getString(R.string.alert_sunrise)
        } else {
            context.getString(R.string.alert_time_for, prayerName(context, alert.prayer))
        }

    /** The prayer's time on the place's clock, which may differ from the device's zone. */
    private fun clock(alert: Fired): String {
        val zone = alert.timeZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(alert.prayerTime.atZone(zone))
    }

    fun prayerName(context: Context, prayer: Prayer?): String = context.getString(
        when (prayer) {
            Prayer.FAJR, null -> R.string.prayer_fajr
            Prayer.SUNRISE -> R.string.prayer_sunrise
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        },
    )
}
