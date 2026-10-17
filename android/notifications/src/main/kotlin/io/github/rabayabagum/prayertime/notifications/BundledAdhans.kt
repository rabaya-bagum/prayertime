package io.github.rabayabagum.prayertime.notifications

import android.content.Context
import android.net.Uri

/**
 * Adhan recordings bundled in res/raw, built by tools/make_adhan_audio.sh.
 * Sources and licenses are in docs/AUDIO_LICENSES.md. An app's
 * [PrayerScheduleSource] can return these from adhanUri and adhanClipUri.
 */
object BundledAdhans {
    fun adhanUri(context: Context, sound: String): Uri? =
        uri(context, when (sound) {
            "azeez" -> R.raw.azeez_adhan
            else -> null
        })

    fun clipUri(context: Context, sound: String): Uri? =
        uri(context, when (sound) {
            "azeez" -> R.raw.azeez_clip
            else -> null
        })

    private fun uri(context: Context, resource: Int?): Uri? =
        resource?.let { Uri.parse("android.resource://${context.packageName}/$it") }
}
