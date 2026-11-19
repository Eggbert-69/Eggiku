package eu.kanade.tachiyomi.ui.reader.eggiku

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.core.graphics.scale
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import exh.log.xLogE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max
import kotlin.math.min

// KMK -->
/**
 * Eggiku panel-by-panel (guided view) for the paged readers. Each tap forward zooms to the next
 * panel; after the last panel the page turns. Panels are found from what's on screen by cutting
 * the page along its gutters (plain strips between panels), top to bottom, then across in
 * reading order.
 */
object EggikuGuided {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val pref by lazy { preferenceStore.getBoolean("eggiku_guided_view", false) }
    private val mutableActive by lazy { MutableStateFlow(pref.get()) }
    val active: StateFlow<Boolean> by lazy { mutableActive.asStateFlow() }

    fun toggle(): Boolean {
        val on = !mutableActive.value
        mutableActive.value = on
        pref.set(on)
        return on
    }

    private const val SCALE_DOWN = 3
    private const val UNIFORM_SPREAD = 40
    private const val MIN_GUTTER = 2 // pixels in the scaled capture

    /** A page's panels, in source image coordinates, and which one is showing (-1 = whole page) */
    class PagePanels(val panels: List<RectF>) {
        var index = -1
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Captures the page as it's shown right now (it must be fully visible) and finds its panels.
     * Calls [onResult] on the main thread with an empty list if nothing useful was found.
     */
    fun detect(view: SubsamplingScaleImageView, rtl: Boolean, onResult: (List<RectF>) -> Unit) {
        val window = view.context.findActivity()?.window
        if (window == null || view.width == 0 || view.height == 0 || !view.isReady) {
            onResult(emptyList())
            return
        }
        // Where the image sits inside the view, so letterboxing isn't mistaken for gutters
        val topLeft = view.sourceToViewCoord(0f, 0f) ?: return onResult(emptyList())
        val bottomRight = view.sourceToViewCoord(view.sWidth.toFloat(), view.sHeight.toFloat()) ?: return onResult(emptyList())
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = max(0, topLeft.x.toInt())
        val top = max(0, topLeft.y.toInt())
        val right = min(view.width, bottomRight.x.toInt())
        val bottom = min(view.height, bottomRight.y.toInt())
        if (right - left < 50 || bottom - top < 50) return onResult(emptyList())
        val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(
                window,
                Rect(location[0] + left, location[1] + top, location[0] + right, location[1] + bottom),
                bitmap,
                { result ->
                    if (result != PixelCopy.SUCCESS) {
                        onResult(emptyList())
                        return@request
                    }
                    val panels = try {
                        findPanels(bitmap, rtl).mapNotNull { r ->
                            // Back to the image's own coordinates
                            val a = view.viewToSourceCoord(PointF((r.left + left).toFloat(), (r.top + top).toFloat()))
                            val b = view.viewToSourceCoord(PointF((r.right + left).toFloat(), (r.bottom + top).toFloat()))
                            if (a == null || b == null) null else RectF(a.x, a.y, b.x, b.y)
                        }
                    } catch (e: Exception) {
                        xLogE("Guided view couldn't find panels", e)
                        emptyList()
                    }
                    bitmap.recycle()
                    onResult(panels)
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            xLogE("Guided view capture failed", e)
            onResult(emptyList())
        }
    }

    /** Zooms so [panel] (source coordinates) fills the screen, with a little room around it */
    fun zoomTo(view: SubsamplingScaleImageView, panel: RectF) {
        val w = panel.width().coerceAtLeast(1f)
        val h = panel.height().coerceAtLeast(1f)
        val scale = (min(view.width / w, view.height / h) * 0.94f).coerceAtLeast(view.minScale)
        (view.animateScaleAndCenter(scale, PointF(panel.centerX(), panel.centerY())) ?: return)
            .withDuration(320)
            .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
            .withInterruptible(true)
            .start()
    }

    fun zoomOut(view: SubsamplingScaleImageView) {
        (view.animateScaleAndCenter(view.minScale, view.sWidth / 2f, view.sHeight / 2f) ?: return)
            .withDuration(280)
            .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
            .withInterruptible(true)
            .start()
    }

    private fun SubsamplingScaleImageView.animateScaleAndCenter(scale: Float, x: Float, y: Float) =
        animateScaleAndCenter(scale, PointF(x, y))

    // region Panel finding

    /** Panels in [bitmap] coordinates, in reading order */
    internal fun findPanels(full: Bitmap, rtl: Boolean): List<Rect> {
        val small = full.scale(max(1, full.width / SCALE_DOWN), max(1, full.height / SCALE_DOWN))
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== full) small.recycle()
        val lum = IntArray(w * h) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        val minW = w / 8
        val minH = h / 12
        val found = mutableListOf<Rect>()
        cut(lum, w, Rect(0, 0, w, h), horizontal = true, rtl = rtl, minW = minW, minH = minH, depth = 0, out = found)
        // A single "panel" covering the whole page means there's nothing to guide through
        if (found.size <= 1) return emptyList()
        return found.map { Rect(it.left * SCALE_DOWN, it.top * SCALE_DOWN, it.right * SCALE_DOWN, it.bottom * SCALE_DOWN) }
    }

    private fun uniformRow(lum: IntArray, w: Int, y: Int, x0: Int, x1: Int): Boolean {
        var lo = 255
        var hi = 0
        var x = x0
        while (x < x1) {
            val v = lum[y * w + x]
            if (v < lo) lo = v
            if (v > hi) hi = v
            if (hi - lo > UNIFORM_SPREAD) return false
            x += 2
        }
        return true
    }

    private fun uniformColumn(lum: IntArray, w: Int, x: Int, y0: Int, y1: Int): Boolean {
        var lo = 255
        var hi = 0
        var y = y0
        while (y < y1) {
            val v = lum[y * w + x]
            if (v < lo) lo = v
            if (v > hi) hi = v
            if (hi - lo > UNIFORM_SPREAD) return false
            y += 2
        }
        return true
    }

    /** Splits [r] into the stretches between gutters along one direction */
    private fun bands(gutter: (Int) -> Boolean, from: Int, to: Int, minSize: Int): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = -1
        var gap = 0
        for (i in from until to) {
            if (gutter(i)) {
                gap++
                if (start >= 0 && gap >= MIN_GUTTER) {
                    val end = i - gap
                    if (end - start + 1 >= minSize) result += start..end
                    start = -1
                }
            } else {
                if (start < 0) start = i
                gap = 0
            }
        }
        if (start >= 0 && to - start >= minSize) result += start until to
        return result
    }

    private fun cut(
        lum: IntArray,
        w: Int,
        r: Rect,
        horizontal: Boolean,
        rtl: Boolean,
        minW: Int,
        minH: Int,
        depth: Int,
        out: MutableList<Rect>,
    ) {
        val rows = bands({ y -> uniformRow(lum, w, y, r.left, r.right) }, r.top, r.bottom, minH)
        val cols = bands({ x -> uniformColumn(lum, w, x, r.top, r.bottom) }, r.left, r.right, minW)
        val first = if (horizontal) rows else cols
        val second = if (horizontal) cols else rows
        val (pieces, alongRows) = when {
            first.size > 1 -> first to horizontal
            second.size > 1 -> second to !horizontal
            else -> {
                // Can't split further: keep it, trimmed to where its content is
                val trimmed = Rect(
                    cols.firstOrNull()?.first ?: r.left,
                    rows.firstOrNull()?.first ?: r.top,
                    (cols.lastOrNull()?.last ?: (r.right - 1)) + 1,
                    (rows.lastOrNull()?.last ?: (r.bottom - 1)) + 1,
                )
                if (trimmed.width() >= minW && trimmed.height() >= minH) out += trimmed
                return
            }
        }
        if (depth >= 6) {
            out += r
            return
        }
        val ordered = if (!alongRows && rtl) pieces.reversed() else pieces
        ordered.forEach { range ->
            val sub = if (alongRows) Rect(r.left, range.first, r.right, range.last + 1) else Rect(range.first, r.top, range.last + 1, r.bottom)
            cut(lum, w, sub, horizontal = !alongRows, rtl = rtl, minW = minW, minH = minH, depth = depth + 1, out = out)
        }
    }

    // endregion
}
// KMK <--
