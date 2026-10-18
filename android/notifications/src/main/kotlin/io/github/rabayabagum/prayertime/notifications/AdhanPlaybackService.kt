package io.github.rabayabagum.prayertime.notifications

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Plays the full adhan in a mediaPlayback foreground service. Started from an
 * exact alarm, which is an allowed exemption for background service starts.
 * Respects the ringer: on silent or vibrate it posts a silent notification
 * instead of playing.
 */
class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alert = intent?.let(Fired::from)
        if (intent?.action == ACTION_STOP || alert == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // startForeground must come first: the system kills services that don't call it within seconds.
        val notification = Alerts.playing(this, alert, stopIntent(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val audio = getSystemService(AudioManager::class.java)
        val uri = alert.sound?.let { PrayerScheduler.source(this).adhanUri(it, alert.prayer) }
        if (uri == null || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            Alerts.postSilent(this, alert)
            stopSelf()
            return START_NOT_STICKY
        }
        play(audio, uri)
        handler.postDelayed({ stopSelf() }, MAX_PLAY_MS)
        return START_NOT_STICKY
    }

    private fun play(audio: AudioManager, uri: android.net.Uri) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSelf()
            }
            .build()
        focusRequest = request
        audio.requestAudioFocus(request)
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(this@AdhanPlaybackService, uri)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stopSelf() }
            setOnErrorListener { _, _, _ -> stopSelf(); true }
            prepareAsync()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY = "io.github.rabayabagum.prayertime.PLAY_ADHAN"
        private const val ACTION_STOP = "io.github.rabayabagum.prayertime.STOP_ADHAN"
        private const val NOTIFICATION_ID = 0x0ADA
        /** Adhan recordings run 3–5 minutes; stop regardless after this. */
        private const val MAX_PLAY_MS = 6 * 60 * 1000L

        /** Returns false when the system refuses a background start, so the caller can post a clip instead. */
        internal fun start(context: Context, alert: Fired): Boolean {
            val intent = alert.writeTo(Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_PLAY))
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) extends IllegalStateException.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e !is ForegroundServiceStartNotAllowedException) throw e
                false
            }
        }

        private fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context, 0,
            Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
