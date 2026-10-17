package io.github.rabayabagum.prayertime.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.rabayabagum.prayertime.core.AlarmWindowPlanner
import io.github.rabayabagum.prayertime.core.ScheduleInputs
import java.time.Instant

/**
 * What the app supplies to the scheduler. The Application class implements
 * [PrayerScheduleSourceProvider] so receivers can reach it after a reboot.
 */
interface PrayerScheduleSource {
    /** Current place and preferences, or null before onboarding. Called off the main thread. */
    fun inputs(): ScheduleInputs?

    /** The full adhan recording for a sound id, played by the foreground service. */
    fun adhanUri(sound: String): Uri?

    /** A clip under 30 s, used as the channel sound when alarms are inexact and no service can start. */
    fun adhanClipUri(sound: String): Uri?

    /** Opens the app; used for notification taps and the alarm-clock "show" intent. */
    fun openAppIntent(context: Context): PendingIntent
}

interface PrayerScheduleSourceProvider {
    val prayerScheduleSource: PrayerScheduleSource
}

/**
 * Entry point for the app: call [sync] on launch and whenever the place or
 * any setting changes. Receivers call it on every alarm, boot, time change and
 * time-zone change, so the 48-hour window keeps rolling.
 */
object PrayerScheduler {
    private const val PREFS = "prayertime_scheduler"
    private const val KEY_ARMED = "armed_request_codes"

    @Synchronized
    fun sync(context: Context, now: Instant = Instant.now()) {
        val app = context.applicationContext
        val inputs = source(app).inputs()
        val desired = if (inputs == null) emptyList() else AlarmWindowPlanner.plan(inputs, now)
        Channels.ensure(app, source(app), desired.mapNotNull { it.sound }.toSet())

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_ARMED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val armed = AlarmWindowPlanner.sync(desired, previous, AndroidAlarmClient(app, inputs?.place?.timeZoneId))
        prefs.edit().putStringSet(KEY_ARMED, armed.map(Int::toString).toSet()).apply()
    }

    /** False on Android 12+ until the user grants "Alarms & reminders"; alarms then fall back to inexact windows. */
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Opens the "Alarms & reminders" toggle for this app. Re-check [canScheduleExactAlarms] in onResume. */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system battery-optimization list. The direct one-tap request
     * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) is restricted by Play policy,
     * so onboarding sends users here with instructions, plus OEM-specific
     * guidance for Xiaomi and Samsung (see dontkillmyapp.com).
     */
    fun batteryOptimizationSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    internal fun source(context: Context): PrayerScheduleSource =
        (context.applicationContext as? PrayerScheduleSourceProvider)?.prayerScheduleSource
            ?: error("The Application class must implement PrayerScheduleSourceProvider")
}
