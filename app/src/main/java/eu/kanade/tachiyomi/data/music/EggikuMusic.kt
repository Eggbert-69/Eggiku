package eu.kanade.tachiyomi.data.music

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

// KMK -->

/**
 * Eggiku Reading music: shows and controls whatever the user is already playing in YouTube Music
 * (or any other music app) through Android's media sessions. Eggiku never plays, downloads or
 * extracts audio itself, and only reads the media info that the music app shares.
 */
object EggikuMusic {

    const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

    data class NowPlaying(
        val packageName: String,
        val appName: String,
        val title: String,
        val artist: String,
        val art: Bitmap?,
        /** Usually the playlist or album name the music app reports */
        val queueTitle: String?,
        val isPlaying: Boolean,
        val durationMs: Long,
        /** Position at [positionUpdatedAt] (elapsedRealtime) */
        val positionMs: Long,
        val positionUpdatedAt: Long,
        val speed: Float,
    ) {
        /** Identifies the song, so the progress bar can snap when it changes */
        val trackKey: String get() = "$packageName|$title|$artist|$durationMs"

        fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long {
            if (!isPlaying) return positionMs
            val elapsed = ((now - positionUpdatedAt) * speed).toLong()
            return (positionMs + elapsed).coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        }
    }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** What's playing right now, or null when nothing is or there's no access */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var users = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        pickController(sessions.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onQueueTitleChanged(title: CharSequence?) = publish()
        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    fun isAccessGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts watching media sessions. Every [start] needs a matching [stop]. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        users++
        _hasAccess.value = isAccessGranted(app)
        if (users > 1 && sessionManager != null) {
            publish()
            return
        }
        if (!_hasAccess.value) return
        try {
            val manager = app.getSystemService<MediaSessionManager>() ?: return
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(app), mainHandler)
            sessionManager = manager
            refreshSessions()
        } catch (e: SecurityException) {
            // Access was turned off in the meantime
            logcat(LogPriority.WARN, e) { "Eggiku music: no notification access" }
            _hasAccess.value = false
            sessionManager = null
        }
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
            // Already gone
        }
        sessionManager = null
        controller?.unregisterCallback(controllerCallback)
        controller = null
        _nowPlaying.value = null
    }

    /** Re-checks access, e.g. after coming back from the system settings screen */
    fun recheckAccess(context: Context) {
        val granted = isAccessGranted(context)
        if (granted == _hasAccess.value && (!granted || sessionManager != null)) return
        val count = users
        users = 0
        stop()
        repeat(count.coerceAtLeast(0)) { start(context) }
    }

    private fun listenerComponent(context: Context) =
        ComponentName(context, EggikuMediaListenerService::class.java)

    private fun refreshSessions() {
        val app = appContext ?: return
        val manager = sessionManager ?: return
        val sessions = try {
            manager.getActiveSessions(listenerComponent(app))
        } catch (_: SecurityException) {
            _hasAccess.value = false
            emptyList()
        }
        pickController(sessions)
    }

    /** YouTube Music wins; otherwise the most recent active session (the system lists those first) */
    private fun pickController(sessions: List<MediaController>) {
        val chosen = sessions.firstOrNull { it.packageName == YOUTUBE_MUSIC_PACKAGE && it.hasContent() }
            ?: sessions.firstOrNull { it.isPlaying() }
            ?: sessions.firstOrNull { it.hasContent() }
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = chosen
            chosen?.registerCallback(controllerCallback, mainHandler)
        }
        publish()
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING
    private fun MediaController.hasContent() = metadata != null || playbackState != null

    private fun publish() {
        val c = controller
        val app = appContext
        if (c == null || app == null) {
            _nowPlaying.value = null
            return
        }
        val metadata = c.metadata
        val state = c.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank() && state == null) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlaying(
            packageName = c.packageName,
            appName = appLabel(app, c.packageName),
            title = title.orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: "",
            art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            queueTitle = c.queueTitle?.toString()?.takeIf { it.isNotBlank() },
            isPlaying = state?.state == PlaybackState.STATE_PLAYING ||
                state?.state == PlaybackState.STATE_BUFFERING,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    private val labelCache = mutableMapOf<String, String>()

    private fun appLabel(context: Context, packageName: String): String = labelCache.getOrPut(packageName) {
        if (packageName == YOUTUBE_MUSIC_PACKAGE) return@getOrPut "YouTube Music"
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    // region Controls

    fun play() = transport { play() }
    fun pause() = transport { pause() }
    fun next() = transport { skipToNext() }
    fun previous() = transport { skipToPrevious() }
    fun togglePlayPause() {
        if (_nowPlaying.value?.isPlaying == true) pause() else play()
    }

    fun setVolumeFraction(context: Context, fraction: Float, baseVolume: Int) {
        val audio = context.getSystemService<android.media.AudioManager>() ?: return
        val target = (baseVolume * fraction).toInt().coerceAtLeast(0)
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes
        }
    }

    fun currentVolume(context: Context): Int =
        context.getSystemService<android.media.AudioManager>()
            ?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            ?: 0

    private inline fun transport(action: MediaController.TransportControls.() -> Unit) {
        try {
            controller?.transportControls?.action()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Eggiku music: control failed" }
        }
    }

    // endregion

    // region Playlists

    /** Opens the link in YouTube Music (or the browser without it), then presses play once its session shows up */
    suspend fun openPlaylist(context: Context, url: String): Boolean {
        val uri = url.trim().toUri()
        // From a screen, open on top of Eggiku so Back returns to the reader
        val flags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        val inApp = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(YOUTUBE_MUSIC_PACKAGE)
            .addFlags(flags)
        val opened = try {
            context.startActivity(inApp)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(flags))
            } catch (_: ActivityNotFoundException) {
                return false
            }
            false
        }
        if (!opened || !isAccessGranted(context)) return true

        // Give YouTube Music a moment to load the playlist, then make sure it's playing
        start(context)
        try {
            withTimeoutOrNull(15_000) {
                while (true) {
                    refreshSessions()
                    val current = _nowPlaying.value
                    if (current?.packageName == YOUTUBE_MUSIC_PACKAGE) {
                        if (!current.isPlaying) {
                            delay(1_500)
                            if (_nowPlaying.value?.isPlaying != true) play()
                        }
                        break
                    }
                    delay(500)
                }
            }
        } finally {
            stop()
        }
        return true
    }

    fun isPlaylistLink(text: String): Boolean {
        val uri = try {
            text.trim().toUri()
        } catch (_: Exception) {
            return false
        }
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    // endregion
}
// KMK <--
