package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku Cool egg mode: keeps downloads from cooking the phone.
 *
 * It looks at Android's own thermal status (Android 10+) and the battery temperature every
 * [CHECK_INTERVAL_MS]. When the phone is warm, downloads drop to one series and two pages at a
 * time; when it's hot, they pause and pick back up by themselves once it has cooled down.
 * Thresholds have a gap between "getting warm" and "cool again" so it doesn't flip back and forth.
 */
object EggikuCoolEgg {

    enum class Heat { COOL, WARM, HOT }

    /** Most series downloaded at the same time while the phone is warm */
    const val WARM_SOURCE_LIMIT = 1

    /** Most pages of a chapter downloaded at the same time while the phone is warm */
    const val WARM_PAGE_LIMIT = 2

    private const val CHECK_INTERVAL_MS = 20_000L

    // Battery temperatures, in tenths of a degree Celsius like Android reports them
    private const val WARM_AT = 410 // 41.0 °C
    private const val HOT_AT = 450 // 45.0 °C
    private const val COOL_BELOW = 390 // 39.0 °C
    private const val NOT_HOT_BELOW = 430 // 43.0 °C

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun enabled() = preferenceStore.getBoolean("eggiku_cool_egg", true)

    /** The current heat level, worked out right now (no waiting) */
    fun current(context: Context, previous: Heat = Heat.COOL): Heat {
        if (!enabled().get()) return Heat.COOL
        return classify(thermalStatus(context), batteryTenths(context), previous)
    }

    /**
     * Heat level over time, while someone is listening. Always [Heat.COOL] when Cool egg mode is
     * turned off.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun heat(context: Context): Flow<Heat> {
        val app = context.applicationContext
        return enabled().changes()
            .distinctUntilChanged()
            .transformLatest { on ->
                if (!on) {
                    emit(Heat.COOL)
                    return@transformLatest
                }
                var level = Heat.COOL
                while (true) {
                    level = classify(thermalStatus(app), batteryTenths(app), level)
                    emit(level)
                    delay(CHECK_INTERVAL_MS)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    internal fun classify(thermal: Int, batteryTenths: Int, previous: Heat): Heat {
        // Android's thermal levels: 0 none, 1 light, 2 moderate, 3 severe and up
        val hot = thermal >= THERMAL_MODERATE || batteryTenths >= HOT_AT
        if (hot) return Heat.HOT
        val warm = thermal >= THERMAL_LIGHT || batteryTenths >= WARM_AT
        return when (previous) {
            // Stay paused until it's properly cooled off, not just one degree under
            Heat.HOT -> when {
                batteryTenths >= NOT_HOT_BELOW -> Heat.HOT
                warm || batteryTenths >= COOL_BELOW -> Heat.WARM
                else -> Heat.COOL
            }
            Heat.WARM -> if (warm || batteryTenths >= COOL_BELOW) Heat.WARM else Heat.COOL
            Heat.COOL -> if (warm) Heat.WARM else Heat.COOL
        }
    }

    private fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            ContextCompat.getSystemService(context, PowerManager::class.java)?.currentThermalStatus ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** Battery temperature in tenths of a degree, or 0 if the phone doesn't say */
    private fun batteryTenths(context: Context): Int {
        return try {
            // Sticky broadcast: reading it doesn't register anything or wake anything up
            val status = ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private const val THERMAL_LIGHT = 1
    private const val THERMAL_MODERATE = 2
}
// KMK <--
