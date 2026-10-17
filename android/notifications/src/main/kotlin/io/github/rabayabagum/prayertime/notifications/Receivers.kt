package io.github.rabayabagum.prayertime.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.rabayabagum.prayertime.core.AlertKind
import io.github.rabayabagum.prayertime.core.PlannedAlert
import io.github.rabayabagum.prayertime.core.Prayer
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.thread

/** Fires for each armed alert: plays or posts it, then rolls the alarm window forward. */
class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alert = Fired.from(intent)
        val pending = goAsync()
        thread(name = "prayer-alert") {
            try {
                if (alert != null) deliver(context.applicationContext, alert)
                PrayerScheduler.sync(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun deliver(context: Context, alert: Fired) {
        val late = Duration.between(alert.time, Instant.now()) > MAX_LATENESS
        when (alert.kind) {
            AlertKind.ADHAN -> when {
                // Device was off or asleep through the prayer: no audio, just say when it began.
                late -> Alerts.postBegan(context, alert)
                alert.exact && AdhanPlaybackService.start(context, alert) -> Unit
                else -> Alerts.postAdhanClip(context, alert)
            }
            AlertKind.SILENT -> if (late) Alerts.postBegan(context, alert) else Alerts.postSilent(context, alert)
            AlertKind.REMINDER -> if (!late) Alerts.postReminder(context, alert)
            AlertKind.KEEP_ALIVE -> Unit // iOS only
        }
    }

    companion object {
        private const val ACTION = "io.github.rabayabagum.prayertime.ALERT"
        private val MAX_LATENESS: Duration = Duration.ofMinutes(10)

        internal fun baseIntent(context: Context): Intent =
            Intent(context, AlertReceiver::class.java).setAction(ACTION)

        internal fun intent(context: Context, alert: PlannedAlert, exact: Boolean, timeZoneId: String?): Intent =
            Fired(
                kind = alert.kind,
                prayer = alert.prayer,
                time = alert.time,
                prayerTime = alert.prayerTime ?: alert.time,
                sound = alert.sound,
                exact = exact,
                notificationId = alert.requestCode,
                timeZoneId = timeZoneId,
            ).writeTo(baseIntent(context))
    }
}

/** Re-arms the window after reboot, app update, clock or time-zone changes, and exact-alarm permission grants. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread(name = "prayer-resync") {
            try {
                PrayerScheduler.sync(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A fired alert as carried in intent extras. */
internal data class Fired(
    val kind: AlertKind,
    val prayer: Prayer?,
    val time: Instant,
    val prayerTime: Instant,
    val sound: String?,
    val exact: Boolean,
    val notificationId: Int,
    val timeZoneId: String?,
) {
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(KIND, kind.name)
        .putExtra(PRAYER, prayer?.name)
        .putExtra(TIME, time.toEpochMilli())
        .putExtra(PRAYER_TIME, prayerTime.toEpochMilli())
        .putExtra(SOUND, sound)
        .putExtra(EXACT, exact)
        .putExtra(NOTIFICATION_ID, notificationId)
        .putExtra(ZONE, timeZoneId)

    companion object {
        private const val KIND = "kind"
        private const val PRAYER = "prayer"
        private const val TIME = "time"
        private const val PRAYER_TIME = "prayer_time"
        private const val SOUND = "sound"
        private const val EXACT = "exact"
        private const val NOTIFICATION_ID = "notification_id"
        private const val ZONE = "zone"

        fun from(intent: Intent): Fired? {
            val kind = intent.getStringExtra(KIND)?.let(AlertKind::valueOf) ?: return null
            return Fired(
                kind = kind,
                prayer = intent.getStringExtra(PRAYER)?.let(Prayer::valueOf),
                time = Instant.ofEpochMilli(intent.getLongExtra(TIME, 0)),
                prayerTime = Instant.ofEpochMilli(intent.getLongExtra(PRAYER_TIME, 0)),
                sound = intent.getStringExtra(SOUND),
                exact = intent.getBooleanExtra(EXACT, false),
                notificationId = intent.getIntExtra(NOTIFICATION_ID, 0),
                timeZoneId = intent.getStringExtra(ZONE),
            )
        }
    }
}
