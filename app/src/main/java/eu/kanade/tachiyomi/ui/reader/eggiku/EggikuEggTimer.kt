package eu.kanade.tachiyomi.ui.reader.eggiku

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Choreographer
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.stats.EggikuEggCarton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

// KMK -->

/**
 * Eggiku egg timer: pick a reading session length and an egg cooks in the corner of the reader.
 * Only time spent in the reader counts. When it's done the egg hatches into the week's egg carton.
 */
object EggikuEggTimer {

    data class Session(
        val totalSeconds: Int,
        val elapsedSeconds: Int = 0,
        val hatched: Boolean = false,
    ) {
        val minutes: Int get() = totalSeconds / 60
        val progress: Float get() = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
        val secondsLeft: Int get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    val LENGTHS = listOf(10, 20, 30, 45, 60)

    private val mutableSession = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = mutableSession.asStateFlow()

    /** The session picker in the reader */
    val pickerOpen = MutableStateFlow(false)

    fun start(minutes: Int) {
        mutableSession.value = Session(totalSeconds = minutes * 60)
    }

    fun cancel() {
        mutableSession.value = null
    }

    /** Called once a second while the reader is open and in front */
    fun tick() {
        var hatchedNow: Session? = null
        mutableSession.update { current ->
            if (current == null || current.hatched) return@update current
            val next = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
            if (next.elapsedSeconds >= next.totalSeconds) {
                next.copy(hatched = true).also { hatchedNow = it }
            } else {
                next
            }
        }
        hatchedNow?.let { EggikuEggCarton.add(it.minutes) }
    }

    fun dismissHatched() {
        if (mutableSession.value?.hatched == true) mutableSession.value = null
    }

    /** A running egg waits for you to come back; a hatched one is already in the carton */
    fun onReaderClosed() {
        dismissHatched()
        pickerOpen.value = false
    }
}

/** Eggiku tilt to scroll for webtoons: on for this reading session only */
object EggikuTilt {
    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    fun toggle(): Boolean {
        mutableActive.value = !mutableActive.value
        return mutableActive.value
    }

    fun off() {
        mutableActive.value = false
    }
}

/**
 * Scrolls a webtoon as you tilt the phone. The angle you're holding it at when it starts (or when
 * the menu closes) is "still"; tilting the top away scrolls down, toward you scrolls up.
 */
class EggikuTiltScroller(
    context: Context,
    private val target: () -> RecyclerView?,
    private val paused: () -> Boolean,
    private val speedLevel: () -> Int,
) : SensorEventListener, Choreographer.FrameCallback {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val density = context.resources.displayMetrics.density

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var pitch: Float? = null
    private var baseline: Float? = null
    private var wasPaused = false
    private var running = false
    private var lastFrameNanos = 0L
    private var carry = 0f

    val available: Boolean get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = true
        pitch = null
        baseline = null
        lastFrameNanos = 0L
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val degrees = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val smoothed = pitch?.let { it + (degrees - it) * 0.15f } ?: degrees
        pitch = smoothed
        if (baseline == null) baseline = smoothed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        val current = pitch
        if (paused()) {
            wasPaused = true
            carry = 0f
        } else if (current != null) {
            if (wasPaused) {
                // Whatever angle you hold it at when the menu closes becomes "still"
                baseline = current
                wasPaused = false
            }
            val delta = current - (baseline ?: current)
            val excess = abs(delta) - DEAD_ZONE
            if (excess > 0f && dt in 0f..0.1f) {
                val fraction = (excess / FULL_SPEED_AT).coerceAtMost(1f).pow(1.6f)
                val maxDpPerSecond = when (speedLevel()) {
                    1 -> 260f
                    3 -> 900f
                    else -> 520f
                }
                carry += sign(delta) * fraction * maxDpPerSecond * density * dt
                val dy = carry.toInt()
                if (dy != 0) {
                    carry -= dy
                    target()?.scrollBy(0, dy)
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private companion object {
        /** Degrees of tilt that do nothing, so holding still stays still */
        const val DEAD_ZONE = 4f

        /** Degrees past the dead zone for full speed */
        const val FULL_SPEED_AT = 16f
    }
}
// KMK <--
