package io.github.rabayabagum.prayertime

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.rabayabagum.prayertime.core.Prayer
import io.github.rabayabagum.prayertime.core.ScheduleInputs
import io.github.rabayabagum.prayertime.data.AppActions
import io.github.rabayabagum.prayertime.data.AppState
import io.github.rabayabagum.prayertime.data.CityDatabase
import io.github.rabayabagum.prayertime.data.StoredState
import io.github.rabayabagum.prayertime.notifications.BundledAdhans
import io.github.rabayabagum.prayertime.notifications.PrayerScheduleSource
import io.github.rabayabagum.prayertime.notifications.PrayerScheduleSourceProvider
import io.github.rabayabagum.prayertime.notifications.PrayerScheduler
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * Owns the saved state and hands it to the scheduler. Receivers reach it after a
 * reboot through [PrayerScheduleSourceProvider], before any activity exists.
 */
class PrayerTimeApplication : Application(), PrayerScheduleSourceProvider {

    /** Loaded on first use: ~34k rows, so off the main thread where possible. */
    val cities: CityDatabase by lazy { assets.open("cities.tsv").reader().use(::CityDatabase) }

    private val prefs by lazy { getSharedPreferences("prayertime", Context.MODE_PRIVATE) }

    @Volatile
    private var cached: AppState? = null

    /** The saved state, or a starting state for the device's zone on first launch. */
    @Synchronized
    fun loadState(): AppState = cached
        ?: (StoredState.decode(prefs.getString(KEY_STATE, null)) ?: AppActions.initial(cities, ZoneId.systemDefault().id))
            .also { cached = it }

    @Synchronized
    fun saveState(state: AppState) {
        cached = state
        prefs.edit().putString(KEY_STATE, StoredState.encode(state)).apply()
    }

    /** Re-arms the alarm window off the main thread. Safe to call often. */
    fun resync() {
        thread(name = "prayer-sync") { PrayerScheduler.sync(this) }
    }

    override fun onCreate() {
        super.onCreate()
        resync()
    }

    override val prayerScheduleSource: PrayerScheduleSource = object : PrayerScheduleSource {
        override fun inputs(): ScheduleInputs =
            loadState().let { ScheduleInputs(it.location.place, it.settings, it.notifications) }

        override fun adhanUri(sound: String, prayer: Prayer?): Uri? =
            BundledAdhans.adhanUri(this@PrayerTimeApplication, sound, prayer)

        override fun adhanClipUri(sound: String): Uri? = BundledAdhans.clipUri(this@PrayerTimeApplication, sound)

        override fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}
