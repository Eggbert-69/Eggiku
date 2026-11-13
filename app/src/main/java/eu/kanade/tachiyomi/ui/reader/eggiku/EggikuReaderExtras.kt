package eu.kanade.tachiyomi.ui.reader.eggiku

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max
import kotlin.math.min
import android.graphics.Color as AndroidColor

// KMK -->
/**
 * Eggiku: how fast you read. Keeps a running average of seconds per page (pauses over two minutes
 * are ignored), used for "about X minutes left" and the pages-per-minute stat.
 */
object EggikuReadingSpeed {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun secondsPerPage() = preferenceStore.getFloat("eggiku_seconds_per_page", 0f)

    private var lastPageAt = 0L
    private const val MAX_GAP_MS = 120_000L
    private const val WEIGHT = 0.08f

    fun onPage() {
        val now = System.currentTimeMillis()
        val gap = now - lastPageAt
        lastPageAt = now
        if (gap in 800..MAX_GAP_MS) {
            val seconds = gap / 1000f
            val pref = secondsPerPage()
            val old = pref.get()
            pref.set(if (old <= 0f) seconds else old + (seconds - old) * WEIGHT)
        }
    }

    fun resetClock() {
        lastPageAt = 0L
    }

    /** Minutes left in the chapter, or null until there's enough to go on */
    fun minutesLeft(pagesLeft: Int): Int? {
        val perPage = secondsPerPage().get()
        if (perPage <= 0f || pagesLeft <= 0) return null
        return max(1, (pagesLeft * perPage / 60f).toInt())
    }

    fun pagesPerMinute(): Float? = secondsPerPage().get().takeIf { it > 0f }?.let { 60f / it }
}

/** Eggiku: a light tap on page turns, a firmer one at the end of a chapter */
object EggikuHaptics {
    const val OFF = 0
    const val LIGHT = 1
    const val MEDIUM = 2
    const val STRONG = 3

    fun pageTurn(view: View, strength: Int, chapterEnd: Boolean) {
        if (strength == OFF) return
        val base = when (strength) {
            LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            MEDIUM -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(if (chapterEnd) HapticFeedbackConstants.LONG_PRESS else base)
    }
}

/**
 * Eggiku panel peek: hold on a page and the panel under your finger pops up full-screen; let go
 * and it's gone. The panel is found by looking outward from your finger for the gutters (the
 * plain white or black strips between panels).
 */
object EggikuPanelPeek {

    /** The panel being shown, or null */
    val showing = MutableStateFlow<Bitmap?>(null)

    var lastDownX = 0f
    var lastDownY = 0f

    @Volatile
    var fingerDown = false

    fun close() {
        showing.value = null
    }

    fun open(screenshot: Bitmap, x: Float, y: Float) {
        showing.value = crop(screenshot, x.toInt(), y.toInt())
    }

    private const val STEP = 4 // look at every 4th pixel, plenty for finding gutters
    private const val UNIFORM_SHARE = 0.97f

    private fun crop(bitmap: Bitmap, x: Int, y: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val small = bitmap.scale(w / STEP, h / STEP)
        val sw = small.width
        val sh = small.height
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        fun lum(px: Int, py: Int): Int {
            val c = pixels[py.coerceIn(0, sh - 1) * sw + px.coerceIn(0, sw - 1)]
            return (AndroidColor.red(c) * 299 + AndroidColor.green(c) * 587 + AndroidColor.blue(c) * 114) / 1000
        }
        fun gutter(l: Int) = l > 235 || l < 20

        val cx = (x / STEP).coerceIn(0, sw - 1)
        val cy = (y / STEP).coerceIn(0, sh - 1)
        var left = cx
        var right = cx
        var top = cy
        var bottom = cy
        // Start with a small box and grow each side until it runs into a gutter, twice over so the
        // second pass uses the box the first one found
        val start = min(sw, sh) / 8
        left = (cx - start).coerceAtLeast(0)
        right = (cx + start).coerceAtMost(sw - 1)
        top = (cy - start).coerceAtLeast(0)
        bottom = (cy + start).coerceAtMost(sh - 1)
        repeat(2) {
            fun columnIsGutter(col: Int) = (top..bottom).count { gutter(lum(col, it)) } >= (bottom - top + 1) * UNIFORM_SHARE
            fun rowIsGutter(row: Int) = (left..right).count { gutter(lum(it, row)) } >= (right - left + 1) * UNIFORM_SHARE
            while (left > 0 && !columnIsGutter(left)) left--
            while (right < sw - 1 && !columnIsGutter(right)) right++
            while (top > 0 && !rowIsGutter(top)) top--
            while (bottom < sh - 1 && !rowIsGutter(bottom)) bottom++
        }
        small.recycle()
        // Too small to be a panel: show a generous area around the finger instead
        if (right - left < sw / 6 || bottom - top < sh / 8) {
            left = (cx - sw / 4).coerceAtLeast(0)
            right = (cx + sw / 4).coerceAtMost(sw - 1)
            top = (cy - sh / 6).coerceAtLeast(0)
            bottom = (cy + sh / 6).coerceAtMost(sh - 1)
        }
        val px = (left * STEP).coerceIn(0, w - 1)
        val py = (top * STEP).coerceIn(0, h - 1)
        val pw = ((right - left + 1) * STEP).coerceIn(1, w - px)
        val ph = ((bottom - top + 1) * STEP).coerceIn(1, h - py)
        return Bitmap.createBitmap(bitmap, px, py, pw, ph)
    }
}
// KMK <--
