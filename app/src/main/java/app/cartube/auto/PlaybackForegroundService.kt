package app.cartube.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import app.cartube.MainActivity
import app.cartube.R

/**
 * Keeps the process in the foreground while the car screen is showing video.
 * HyperOS will otherwise freeze the WebView as soon as the phone locks.
 * A media session is registered so a targetSdk 35 mediaPlayback service is
 * actually considered to be playing.
 */
class PlaybackForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var session: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            startInForeground()
            acquireWakeLock()
            requestAudioFocus()
            START_NOT_STICKY
        } catch (t: Exception) {
            Log.e(TAG, "Could not start foreground playback", t)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        session?.apply {
            isActive = false
            release()
        }
        session = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun startInForeground() {
        ensureChannel(this)
        val mediaSession = ensureSession()
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_play)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureSession(): MediaSessionCompat {
        session?.let { return it }
        return MediaSessionCompat(this, "CarTube").also { created ->
            created.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP,
                    )
                    .setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f,
                    )
                    .build(),
            )
            created.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getString(R.string.notif_title))
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.app_name))
                    .build(),
            )
            created.isActive = true
            session = created
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cartube:playback").apply {
            setReferenceCounted(false)
            acquire(WAKE_TIMEOUT_MS)
        }
    }

    private fun requestAudioFocus() {
        if (focusRequest != null) return
        val manager = getSystemService(AudioManager::class.java) ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        manager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    companion object {
        private const val TAG = "CarTube"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        private const val WAKE_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = context.getString(R.string.notif_channel_desc)
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            val appContext = context.applicationContext
            ensureChannel(appContext)
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, PlaybackForegroundService::class.java),
                )
            } catch (t: Exception) {
                Log.w(TAG, "Playback service was not started", t)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, PlaybackForegroundService::class.java))
        }
    }
}
