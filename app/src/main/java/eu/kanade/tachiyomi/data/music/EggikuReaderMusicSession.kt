package eu.kanade.tachiyomi.data.music

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// KMK -->

/**
 * Eggiku: ties Reading music to the reader's lifecycle. Watches media sessions while the reader
 * is visible, pauses music when you leave (and resumes it when you come back) if that's on,
 * and handles "Fade out after 30 minutes".
 */
object EggikuReaderMusicSession {

    private val preferences: EggikuMusicPreferences by lazy { Injekt.get() }

    /** When Eggiku paused the music on leaving the reader; 0 = it didn't */
    private var pausedByUsAt = 0L

    /** Don't surprise anyone by resuming music they left behind hours ago */
    private const val RESUME_WINDOW_MS = 2 * 60 * 60 * 1000L

    private var fadeJob: Job? = null

    fun onReaderStart(context: Context, scope: CoroutineScope) {
        EggikuMusic.start(context)
        val pausedAt = pausedByUsAt
        pausedByUsAt = 0L
        if (pausedAt > 0L && System.currentTimeMillis() - pausedAt < RESUME_WINDOW_MS) {
            // Resume what we paused. The music app's session can take a moment to report in,
            // so try a few times rather than once.
            scope.launch {
                repeat(5) {
                    if (EggikuMusic.nowPlaying.value?.isPlaying == true) return@launch
                    if (EggikuMusic.nowPlaying.value != null) EggikuMusic.play()
                    delay(400)
                }
            }
        }
        if (preferences.fadeOutAfter30Minutes().get()) {
            fadeJob?.cancel()
            fadeJob = scope.launch { fadeOutLater(context) }
        }
    }

    fun onReaderStop(isFinishing: Boolean) {
        fadeJob?.cancel()
        fadeJob = null
        if (preferences.pauseWhenLeavingReader().get() && EggikuMusic.nowPlaying.value?.isPlaying == true) {
            EggikuMusic.pause()
            // Remember it even when the reader closes, so opening any chapter again picks it back up
            pausedByUsAt = System.currentTimeMillis()
        }
        EggikuMusic.stop()
    }

    private suspend fun fadeOutLater(context: Context) {
        delay(30.minutes)
        if (EggikuMusic.nowPlaying.value?.isPlaying != true) return
        val startVolume = EggikuMusic.currentVolume(context)
        if (startVolume <= 0) {
            EggikuMusic.pause()
            return
        }
        // Lower the volume over about 30 seconds, pause, then put the volume back for next time
        val steps = 30
        try {
            for (step in 1..steps) {
                EggikuMusic.setVolumeFraction(context, 1f - step / steps.toFloat(), startVolume)
                delay(1.seconds)
            }
            EggikuMusic.pause()
            delay(1.seconds)
        } finally {
            EggikuMusic.setVolumeFraction(context, 1f, startVolume)
        }
    }
}
// KMK <--
