package eu.kanade.tachiyomi.ui.reader.eggiku

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

// KMK -->

/**
 * Eggiku page tint: picks the main colour of what's on screen in the reader so its menus, bars and
 * mini player can take it on. It copies the screen into a tiny picture rather than reading page
 * files itself, so it never competes with the reader for them. Black-and-white pages give null,
 * and the reader falls back to its usual colours (or the cover colour).
 */
object EggikuPageTint {

    private val mutableColor = MutableStateFlow<Int?>(null)
    val color: StateFlow<Int?> = mutableColor.asStateFlow()

    fun reset() {
        mutableColor.value = null
    }

    /** Samples the reader's window; call with the menu hidden so the bars aren't in the picture */
    fun sampleWindow(activity: Activity, scope: CoroutineScope) {
        val window = activity.window ?: return
        val decor = window.peekDecorView() ?: return
        if (decor.width <= 0 || decor.height <= 0 || !decor.isAttachedToWindow) return
        val bitmap = try {
            Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return
        }
        try {
            PixelCopy.request(
                window,
                bitmap,
                { result ->
                    if (result != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        return@request
                    }
                    scope.launch(Dispatchers.Default) {
                        val picked = try {
                            mainColor(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                        mutableColor.value = picked
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Throwable) {
            bitmap.recycle()
        }
    }

    private const val SAMPLE_WIDTH = 48
    private const val SAMPLE_HEIGHT = 96

    /** The strongest hue on the page, if enough of it is in colour */
    private fun mainColor(bitmap: Bitmap): Int? {
        // Skip the top and bottom strips, where the egg timer, page number and mini player sit,
        // so the tint doesn't end up copying itself
        val width = bitmap.width
        val top = (bitmap.height * 0.08f).toInt()
        val height = (bitmap.height * 0.85f).toInt() - top
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, top, width, height)
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
