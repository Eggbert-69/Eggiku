package eu.kanade.tachiyomi.ui.reader.eggiku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

// KMK -->

/**
 * Eggiku page tint: picks the main colour of the page you're on so the reader's menus, bars and
 * mini player can take it on. Black-and-white pages give null, and the reader falls back to its
 * usual colours (or the cover colour).
 */
object EggikuPageTint {

    private val mutableColor = MutableStateFlow<Int?>(null)
    val color: StateFlow<Int?> = mutableColor.asStateFlow()

    private val cache = LruCache<String, Int>(128)
    private const val NO_COLOR = 0
    private var job: Job? = null

    fun onPage(page: ReaderPage, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            withTimeoutOrNull(20_000) { page.statusFlow.first { it == Page.State.Ready } } ?: return@launch
            val key = "${page.chapter.chapter.id}:${page.index}"
            val sampled = cache.get(key) ?: (sample(page) ?: NO_COLOR).also { cache.put(key, it) }
            mutableColor.value = sampled.takeIf { it != NO_COLOR }
        }
    }

    fun reset() {
        job?.cancel()
        mutableColor.value = null
    }

    private fun sample(page: ReaderPage): Int? = try {
        val stream = page.stream ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) > 24_000) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = stream().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        try {
            mainColor(bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (_: Throwable) {
        null
    }

    /** The strongest hue on the page, if enough of it is in colour */
    private fun mainColor(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bins = 24
        val weight = FloatArray(bins)
        val red = FloatArray(bins)
        val green = FloatArray(bins)
        val blue = FloatArray(bins)
        val hsv = FloatArray(3)
        var colourful = 0
        for (pixel in pixels) {
            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (saturation < 0.28f || value < 0.18f || value > 0.98f) continue
            colourful++
            val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            val w = saturation * value
            weight[bin] += w
            red[bin] += android.graphics.Color.red(pixel) * w
            green[bin] += android.graphics.Color.green(pixel) * w
            blue[bin] += android.graphics.Color.blue(pixel) * w
        }
        // Mostly black and white: no tint
        if (colourful < max(40, pixels.size / 14)) return null
        val best = weight.indices.maxBy { weight[it] }
        val w = weight[best].takeIf { it > 0f } ?: return null
        return android.graphics.Color.rgb((red[best] / w).toInt(), (green[best] / w).toInt(), (blue[best] / w).toInt())
    }
}
// KMK <--
