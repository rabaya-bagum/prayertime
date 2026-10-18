package io.github.rabayabagum.prayertime.notifications

import android.content.Context
import android.net.Uri
import io.github.rabayabagum.prayertime.core.Prayer

/**
 * Adhan recordings bundled in res/raw, built by tools/make_adhan_audio.sh.
 * Sources and licenses are in docs/AUDIO_LICENSES.md. An app's
 * [PrayerScheduleSource] can return these from adhanUri and adhanClipUri.
 */
object BundledAdhans {
    /**
     * Fajr adds "As-salatu khayrun min an-nawm", so it gets its own recording.
     * A reciter without one plays the clip at Fajr, never the regular adhan.
     */
    fun adhanUri(context: Context, sound: String, prayer: Prayer?): Uri? =
        if (prayer == Prayer.FAJR) {
            uri(context, fajr(sound)) ?: clipUri(context, sound)
        } else {
            uri(context, regular(sound))
        }

    fun clipUri(context: Context, sound: String): Uri? =
        uri(context, when (sound) {
            "azeez" -> R.raw.azeez_clip
            else -> null
        })

    private fun regular(sound: String): Int? = when (sound) {
        "azeez" -> R.raw.azeez_adhan
        else -> null
    }

    private fun fajr(sound: String): Int? = when (sound) {
        "azeez" -> R.raw.azeez_fajr_adhan
        else -> null
    }

    private fun uri(context: Context, resource: Int?): Uri? =
        resource?.let { Uri.parse("android.resource://${context.packageName}/$it") }
}
