package eu.kanade.tachiyomi.ui.reader.eggiku

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

// KMK -->
/**
 * Raw translation for one page view. Reads only the part of the page that's on screen and
 * hasn't been read yet, keeps everything in the page's own coordinates and draws it with the
 * page, so translations follow scrolling and zooming without being read again.
 */
class EggikuPageTranslation(private val host: ViewGroup) {

    /** A bubble placed on the page: its box and its text already laid out to fit */
    class Placed(
        val box: RectF,
        val layout: StaticLayout,
        val original: String,
    )

    class Saved(val placed: List<Placed>, val covered: List<RectF>)

    private var view: SubsamplingScaleImageView? = null
    private var overlay: Overlay? = null
    private var placed: List<Placed> = emptyList()
    private var covered: List<RectF> = emptyList()
    private var job: Job? = null
    private var attached = false

    /** Identifies the page (chapter + page + image size), for the cache */
    var pageKey: String? = null

    private val runnable = Runnable { run() }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { onMoved() }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attached = true
            v.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            EggikuTranslate.register(this@EggikuPageTranslation)
            schedule(300)
        }

        override fun onViewDetachedFromWindow(v: View) {
            attached = false
            v.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            EggikuTranslate.unregister(this@EggikuPageTranslation)
            host.removeCallbacks(runnable)
            job?.cancel()
        }
    }

    init {
        host.addOnAttachStateChangeListener(attachListener)
        if (host.isAttachedToWindow) attachListener.onViewAttachedToWindow(host)
    }

    /** A still image went into the page; animated pages aren't translated */
    fun bind(image: SubsamplingScaleImageView) {
        view = image
        if (overlay == null) {
            overlay = Overlay(host.context).also {
                host.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        } else {
            // Keep it on top of the new image
            overlay?.bringToFront()
        }
    }

    /** The page view is being reused for another page */
    fun reset() {
        job?.cancel()
        host.removeCallbacks(runnable)
        placed = emptyList()
        covered = emptyList()
        overlay?.invalidate()
    }

    fun unbind() {
        job?.cancel()
        host.removeCallbacks(runnable)
        view = null
        placed = emptyList()
        covered = emptyList()
        overlay?.let { host.removeView(it) }
        overlay = null
    }

    /** The image finished loading: show anything remembered for it, then read the rest */
    fun onReady() {
        val image = view ?: return
        val key = pageKey?.let { "$it:${image.sWidth}x${image.sHeight}" }
        val saved = key?.let { EggikuTranslate.pageCache.get(it) }
        placed = saved?.placed.orEmpty()
        covered = saved?.covered.orEmpty()
        overlay?.invalidate()
        schedule(350)
    }

    /** Zoomed or panned inside the page */
    fun onMoved() {
        if (placed.isNotEmpty()) overlay?.invalidate()
        schedule(350)
    }

    fun hide() {
        job?.cancel()
        host.removeCallbacks(runnable)
        overlay?.invalidate()
    }

    fun schedule(delayMs: Long) {
        overlay?.invalidate()
        if (!EggikuTranslate.active.value || view == null || !attached) return
        host.removeCallbacks(runnable)
        host.postDelayed(runnable, delayMs)
    }

    private fun run() {
        val image = view ?: return
        if (!EggikuTranslate.active.value || !attached || !image.isReady) return
        if (job?.isActive == true) {
            schedule(400)
            return
        }
        if (EggikuTranslate.menuVisible) return
        val window = host.context.findActivity()?.window ?: return

        // The part of the page that's on screen, in window pixels
        val visible = Rect()
        if (!image.getGlobalVisibleRect(visible)) return
        val location = IntArray(2)
        image.getLocationInWindow(location)
        val topLeft = image.sourceToViewCoord(0f, 0f) ?: return
        val bottomRight = image.sourceToViewCoord(image.sWidth.toFloat(), image.sHeight.toFloat()) ?: return
        val imageOnScreen = Rect(
            location[0] + topLeft.x.toInt(),
            location[1] + topLeft.y.toInt(),
            location[0] + bottomRight.x.toInt(),
            location[1] + bottomRight.y.toInt(),
        )
        if (!visible.intersect(imageOnScreen) || visible.width() < 80 || visible.height() < 80) return

        // How window pixels map back to the page right now
        val scale = image.scale
        val origin = image.viewToSourceCoord(0f, 0f) ?: return
        fun toSource(x: Float, y: Float) = PointF(origin.x + (x - location[0]) / scale, origin.y + (y - location[1]) / scale)
        val a = toSource(visible.left.toFloat(), visible.top.toFloat())
        val b = toSource(visible.right.toFloat(), visible.bottom.toFloat())
        val region = RectF(a.x, a.y, b.x, b.y)
        if (isCovered(region)) return

        job = EggikuTranslate.scope.launch {
            try {
                EggikuTranslate.setCapturing(true)
                awaitFrames(2)
                val bitmap = capture(window, visible)
                EggikuTranslate.setCapturing(false)
                if (bitmap == null) return@launch

                // Already-read parts of the page, in capture pixels, to skip
                val skip = covered.map { c ->
                    RectF(
                        (c.left - region.left) * scale,
                        (c.top - region.top) * scale,
                        (c.right - region.left) * scale,
                        (c.bottom - region.top) * scale,
                    )
                }
                val bubbles = EggikuTranslate.process(bitmap, skip)
                bitmap.recycle()

                // Bubbles cut by the screen edge get read properly once they're fully in view
                val edge = max(region.height() * 0.08f, 40f / scale)
                val cutTop = region.top > 1f
                val cutBottom = region.bottom < image.sHeight - 1f
                val cutLeft = region.left > 1f
                val cutRight = region.right < image.sWidth - 1f
                val readRegion = RectF(region).apply {
                    if (cutTop) top += edge
                    if (cutBottom) bottom -= edge
                    if (cutLeft) left += edge
                    if (cutRight) right -= edge
                }
                val found = mutableListOf<EggikuTranslate.Bubble>()
                bubbles.forEach { bubble ->
                    val r = RectF(
                        region.left + bubble.rect.left / scale,
                        region.top + bubble.rect.top / scale,
                        region.left + bubble.rect.right / scale,
                        region.top + bubble.rect.bottom / scale,
                    )
                    val nearTop = cutTop && r.top - region.top < edge
                    val nearBottom = cutBottom && region.bottom - r.bottom < edge
                    val nearLeft = cutLeft && r.left - region.left < edge
                    val nearRight = cutRight && region.right - r.right < edge
                    if (nearTop || nearBottom || nearLeft || nearRight) {
                        // Leave this bubble's whole area unread, so it's picked up next time
                        if (nearTop) readRegion.top = max(readRegion.top, r.bottom)
                        if (nearBottom) readRegion.bottom = min(readRegion.bottom, r.top)
                        if (nearLeft) readRegion.left = max(readRegion.left, r.right)
                        if (nearRight) readRegion.right = min(readRegion.right, r.left)
                    } else {
                        found += bubble.copy(rect = r)
                    }
                }
                // Anything kept must sit inside what's marked as read
                found.retainAll { readRegion.contains(it.rect.centerX(), it.rect.centerY()) }

                val density = host.resources.displayMetrics.scaledDensity
                val newPlaced = withContext(Dispatchers.Default) {
                    place(found, placed, image.sWidth.toFloat(), image.sHeight.toFloat(), scale, density)
                }
                if (view !== image) return@launch
                placed = placed + newPlaced
                if (readRegion.width() > 0f && readRegion.height() > 0f) covered = covered + readRegion
                pageKey?.let { EggikuTranslate.pageCache.put("$it:${image.sWidth}x${image.sHeight}", Saved(placed, covered)) }
                overlay?.invalidate()
            } catch (e: CancellationException) {
                EggikuTranslate.setCapturing(false)
                throw e
            } catch (e: Exception) {
                EggikuTranslate.setCapturing(false)
                xLogE("Page translation failed", e)
                EggikuTranslate.fail(e.message)
            }
        }
    }

    private fun isCovered(region: RectF): Boolean {
        if (covered.isEmpty()) return false
        // Sample the region; if nearly all of it has been read, there's nothing new
        var inside = 0
        var total = 0
        for (i in 0..6) {
            for (j in 0..6) {
                val x = region.left + region.width() * i / 6f
                val y = region.top + region.height() * j / 6f
                total++
                if (covered.any { it.contains(x, y) }) inside++
            }
        }
        return inside >= total * 0.9f
    }

    private suspend fun awaitFrames(count: Int) {
        repeat(count) {
            suspendCancellableCoroutine { cont ->
                Choreographer.getInstance().postFrameCallback { if (cont.isActive) cont.resume(Unit) }
            }
        }
    }

    private suspend fun capture(window: android.view.Window, rect: Rect): Bitmap? {
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val ok = suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
        if (!ok) bitmap.recycle()
        return if (ok) bitmap else null
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** Draws the translations with the page, scaled to the page's current zoom */
    @SuppressLint("ViewConstructor")
    private inner class Overlay(context: Context) : View(context) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            style = Paint.Style.STROKE
        }

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val image = view ?: return
            if (!EggikuTranslate.active.value || placed.isEmpty() || !image.isReady) return
            val scale = image.scale
            val point = PointF()
            placed.forEach { item ->
                image.sourceToViewCoord(item.box.left, item.box.top, point)
                val w = item.box.width() * scale
                val h = item.box.height() * scale
                // Off screen: skip
                if (point.x > width || point.y > height || point.x + w < 0 || point.y + h < 0) return@forEach
                canvas.save()
                canvas.translate(point.x, point.y)
                canvas.scale(scale, scale)
                val radius = min(item.box.width(), item.box.height()) * 0.18f
                canvas.drawRoundRect(0f, 0f, item.box.width(), item.box.height(), radius, radius, fill)
                edge.strokeWidth = 1f / scale
                canvas.drawRoundRect(0f, 0f, item.box.width(), item.box.height(), radius, radius, edge)
                val pad = (item.box.width() - item.layout.width) / 2f
                canvas.translate(pad, (item.box.height() - item.layout.height) / 2f)
                item.layout.draw(canvas)
                canvas.restore()
            }
        }
    }

    companion object {
        private val textPaint = ThreadLocal.withInitial {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }

        /**
         * Gives each new bubble a box (a bit roomier than the text, never over another bubble)
         * and finds the biggest text size that fits it. Done once, in page pixels.
         */
        internal fun place(
            bubbles: List<EggikuTranslate.Bubble>,
            existing: List<Placed>,
            pageW: Float,
            pageH: Float,
            captureScale: Float,
            scaledDensity: Float,
        ): List<Placed> {
            val taken = existing.map { it.box }.toMutableList()
            val result = mutableListOf<Placed>()
            // Text sizes as they'd look at the zoom the page was read at
            val minPx = 9f * scaledDensity / captureScale
            val maxPx = 22f * scaledDensity / captureScale
            bubbles.sortedByDescending { it.rect.width() * it.rect.height() }.forEach { bubble ->
                val box = roomyBox(bubble, taken, pageW, pageH)
                if (taken.any { RectF.intersects(it, box) }) return@forEach
                taken += box
                val pad = min(box.width(), box.height()) * 0.08f
                val innerW = max(1f, box.width() - pad * 2)
                val innerH = max(1f, box.height() - pad * 2)
                result += Placed(box, fit(bubble.translated, innerW.toInt(), innerH, minPx, maxPx), bubble.original)
            }
            return result
        }

        private fun roomyBox(bubble: EggikuTranslate.Bubble, taken: List<RectF>, pageW: Float, pageH: Float): RectF {
            val base = RectF(bubble.rect)
            val side = min(base.width(), base.height())
            // Text boxes hug the letters; speech bubbles are a little bigger than that
            base.inset(-side * 0.12f, -side * 0.12f)
            // Tall vertical text makes a cramped column for translations, so widen it
            if (bubble.vertical || base.height() > base.width() * 1.6f) {
                val want = min(base.height() * 0.85f, base.width() * 2.4f)
                val grow = (want - base.width()) / 2f
                if (grow > 0) base.inset(-grow, 0f)
            }
            base.intersect(0f, 0f, pageW, pageH)
            // Shrink back towards the text until it's clear of other bubbles
            var box = base
            var tries = 0
            while (taken.any { RectF.intersects(it, box) } && tries < 6) {
                box = RectF(
                    (box.left + bubble.rect.left) / 2f,
                    (box.top + bubble.rect.top) / 2f,
                    (box.right + bubble.rect.right) / 2f,
                    (box.bottom + bubble.rect.bottom) / 2f,
                )
                tries++
            }
            return box
        }

        private fun build(text: String, width: Int, size: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout {
            val paint = TextPaint(textPaint.get()).apply { textSize = size }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(0f, 0.92f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
                .build()
        }

        /** Biggest size whose text fits the box; at the smallest size, it trims with "…" */
        private fun fit(text: String, width: Int, height: Float, minPx: Float, maxPx: Float): StaticLayout {
            var lo = minPx
            var hi = max(minPx, min(maxPx, height))
            var best: StaticLayout? = null
            repeat(8) {
                val mid = (lo + hi) / 2f
                val layout = build(text, width, mid)
                if (layout.height <= height && widestLine(layout) <= width + 0.5f) {
                    best = layout
                    lo = mid
                } else {
                    hi = mid
                }
            }
            best?.let { return it }
            val smallest = build(text, width, minPx)
            if (smallest.height <= height) return smallest
            val lineHeight = smallest.height / max(1, smallest.lineCount).toFloat()
            return build(text, width, minPx, maxLines = max(1, (height / lineHeight).toInt()))
        }

        private fun widestLine(layout: StaticLayout): Float {
            var widest = 0f
            for (i in 0 until layout.lineCount) widest = max(widest, layout.getLineWidth(i))
            return widest
        }
    }
}
// KMK <--
