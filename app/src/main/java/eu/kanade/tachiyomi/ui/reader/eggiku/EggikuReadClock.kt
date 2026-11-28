package eu.kanade.tachiyomi.ui.reader.eggiku

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku read time that ignores an untouched screen.
 *
 * Every touch, key press, page change, and each tick of auto-scroll or tilt-to-scroll marks the
 * reader as active. If nothing happens for longer than the idle limit (Reader settings, 5 minutes
 * by default), the gap stops counting: the chapter is credited up to the last sign of life and
 * the clock picks up again on the next one. With the limit off it behaves like the old timer.
 */
object EggikuReadClock {

    private val preferences: ReaderPreferences by lazy { Injekt.get() }

    /** Time already counted from earlier active stretches of this chapter */
    @Volatile
    private var creditedMs = 0L

    /** Start of the stretch that is still going, or null when the clock is stopped */
    @Volatile
    private var stretchStart: Long? = null

    @Volatile
    private var lastActive = 0L

    private fun idleLimitMs(): Long = preferences.eggikuIdleCutoffMinutes().get().coerceAtLeast(0) * 60_000L

    val running: Boolean get() = stretchStart != null

    fun start(now: Long = System.currentTimeMillis()) {
        creditedMs = 0L
        stretchStart = now
        lastActive = now
    }

    /** Something happened in the reader */
    fun ping(now: Long = System.currentTimeMillis()) {
        val start = stretchStart ?: return
        val limit = idleLimitMs()
        if (limit > 0 && now - lastActive > limit) {
            // Close the old stretch at the last sign of life and start a new one now
            creditedMs += (lastActive - start).coerceAtLeast(0)
            stretchStart = now
        }
        lastActive = now
    }

    /** Counted time so far, without stopping the clock */
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
        val start = stretchStart ?: return 0L
        val limit = idleLimitMs()
        val end = if (limit > 0 && now - lastActive > limit) lastActive else now
        return creditedMs + (end - start).coerceAtLeast(0)
    }

    /** Stops the clock and hands back the counted time */
    fun stop(now: Long = System.currentTimeMillis()): Long {
        val total = elapsedMs(now)
        stretchStart = null
        creditedMs = 0L
        return total
    }
}
// KMK <--
