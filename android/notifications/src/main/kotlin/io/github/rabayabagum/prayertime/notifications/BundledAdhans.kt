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

    /** Whom to credit for a bundled recording. Same entries as the iOS app's AdhanSound. */
    data class Recording(val reciter: String, val credit: String, val license: String, val source: String)

    private val azeez = Recording(
        "Aaqib Azeez", "Recording by Atcovi, Wikimedia Commons", "CC BY-SA 4.0",
        "https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3",
    )
    private val azemiFajr = Recording(
        "Besim Azemi", "Islamic Center Malmö, via Wikimedia Commons", "CC BY 3.0",
        "https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm",
    )

    /** Every bundled recording, for the About screen. */
    val credits: List<Recording> = listOf(azeez, azemiFajr)

    /**
     * Who is heard when the full adhan for [prayer] plays, or null at Fajr when
     * the sound has no Fajr recording (only the takbir clip plays then).
     */
    fun recording(sound: String, prayer: Prayer?): Recording? = when (sound) {
        "azeez" -> if (prayer == Prayer.FAJR) azemiFajr else azeez
        else -> null
    }

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
