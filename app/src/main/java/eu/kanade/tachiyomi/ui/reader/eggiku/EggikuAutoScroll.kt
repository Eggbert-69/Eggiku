package eu.kanade.tachiyomi.ui.reader.eggiku

import android.content.Context
import android.view.Choreographer
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.pow
import kotlin.math.roundToInt

// KMK -->

/**
 * Eggiku smart auto-scroll: moves through the chapter at the speed you normally read, which
 * Eggiku already learns from how long you spend on each page. Touch the screen and it waits;
 * let go and it carries on a moment later. The − and + buttons nudge the speed, and the nudge is
 * remembered separately for scrolling (webtoon) and page-by-page reading.
 */
object EggikuAutoScroll {

    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    /** Speed multiplier, shown as "1.2×" */
    private val mutableSpeed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = mutableSpeed.asStateFlow()

    /** Set by the reader on every touch */
    @Volatile
    var fingerDown = false

    @Volatile
    var lastTouchUpAt = 0L

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Nudge steps from your usual pace, per kind of reader */
    private fun stepsPref(webtoon: Boolean) =
        preferenceStore.getInt(if (webtoon) "eggiku_autoscroll_steps_webtoon" else "eggiku_autoscroll_steps_pager", 0)

    private var webtoon = true

    fun toggle(isWebtoon: Boolean): Boolean {
        webtoon = isWebtoon
        mutableSpeed.value = multiplier(stepsPref(isWebtoon).get())
        mutableActive.value = !mutableActive.value
        return mutableActive.value
    }

    fun off() {
        mutableActive.value = false
    }

    fun faster() = nudge(1)

    fun slower() = nudge(-1)

    private fun nudge(by: Int) {
        val pref = stepsPref(webtoon)
        val steps = (pref.get() + by).coerceIn(MIN_STEPS, MAX_STEPS)
        pref.set(steps)
        mutableSpeed.value = multiplier(steps)
    }

    private fun multiplier(steps: Int): Float = STEP.pow(steps)

    fun label(speed: Float): String = "${(speed * 10).roundToInt() / 10f}×"

    /** Your usual seconds per page, from the reading speed Eggiku keeps */
    fun secondsPerPage(): Float = EggikuReadingSpeed.secondsPerPage().get()
        .takeIf { it > 0f }
        ?.coerceIn(MIN_SECONDS_PER_PAGE, MAX_SECONDS_PER_PAGE)
        ?: DEFAULT_SECONDS_PER_PAGE

    /** True while a touch, or the moment just after one, should hold it still */
    fun holding(now: Long = System.currentTimeMillis()): Boolean =
        fingerDown || now - lastTouchUpAt < RESUME_AFTER_MS

    private const val STEP = 1.15f
    private const val MIN_STEPS = -10
    private const val MAX_STEPS = 10
    private const val MIN_SECONDS_PER_PAGE = 2f
    private const val MAX_SECONDS_PER_PAGE = 60f
    private const val DEFAULT_SECONDS_PER_PAGE = 8f
    const val RESUME_AFTER_MS = 1_200L
}

/**
 * Drives auto-scroll every frame. On a webtoon it glides one screen per [EggikuAutoScroll.secondsPerPage];
 * page by page it turns the page that often. Anything in [paused] (menu open, a dialog) holds it.
 */
class EggikuAutoScroller(
    context: Context,
    private val target: () -> RecyclerView?,
    private val isWebtoon: () -> Boolean,
    private val nextPage: () -> Unit,
    private val paused: () -> Boolean,
) : Choreographer.FrameCallback {

    private val screenHeight = context.resources.displayMetrics.heightPixels
    private var running = false
    private var lastFrameNanos = 0L
    private var carry = 0f
    private var pageElapsed = 0f

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        carry = 0f
        pageElapsed = 0f
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        if (paused() || EggikuAutoScroll.holding()) {
            carry = 0f
            // A touch restarts the page timer, so a page you just turned gets its full time
            pageElapsed = 0f
        } else if (dt in 0f..0.1f) {
            val speed = EggikuAutoScroll.speed.value
            val secondsPerPage = EggikuAutoScroll.secondsPerPage()
            if (isWebtoon()) {
                carry += screenHeight / secondsPerPage * speed * dt
                val dy = carry.toInt()
                if (dy != 0) {
                    carry -= dy
                    target()?.scrollBy(0, dy)
                }
            } else {
                pageElapsed += dt * speed
                if (pageElapsed >= secondsPerPage) {
                    pageElapsed = 0f
                    nextPage()
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
// KMK <--
