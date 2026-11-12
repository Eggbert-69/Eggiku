package eu.kanade.presentation.more.stats

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// KMK -->

/** Eggiku: friendly units for the reading stats */
object EggikuStatsFormat {

    private val imperialCountries = setOf("US", "LR", "MM")

    fun usesImperial(locale: Locale = Locale.getDefault()) = locale.country in imperialCountries

    /** "2.3 mi", "840 ft", "1.2 km", "37 m" */
    fun distance(mm: Double, locale: Locale = Locale.getDefault()): String {
        return if (usesImperial(locale)) {
            val feet = mm / 304.8
            when {
                feet >= 5280 * 0.1 -> String.format(locale, "%.1f mi", feet / 5280)
                feet >= 1 -> String.format(locale, "%,d ft", feet.toLong())
                else -> String.format(locale, "%d in", (mm / 25.4).toLong())
            }
        } else {
            val metres = mm / 1000
            when {
                metres >= 100 -> String.format(locale, "%.1f km", metres / 1000)
                metres >= 1 -> String.format(locale, "%,d m", metres.toLong())
                else -> String.format(locale, "%d cm", (mm / 10).toLong())
            }
        }
    }

    /** Whole hours and leftover minutes */
    fun hoursAndMinutes(seconds: Long): Pair<Long, Long> {
        val minutes = seconds / 60
        return minutes / 60 to minutes % 60
    }

    /** A fun comparison for the recap, e.g. "about the height of the Empire State Building" */
    fun landmark(mm: Double): StringResource? {
        val metres = mm / 1000
        return when {
            metres >= 42_195 -> KMR.strings.stats_landmark_marathon
            metres >= 5_000 -> KMR.strings.stats_landmark_5k
            metres >= 1_609 -> KMR.strings.stats_landmark_mile
            metres >= 828 -> KMR.strings.stats_landmark_burj
            metres >= 443 -> KMR.strings.stats_landmark_empire
            metres >= 300 -> KMR.strings.stats_landmark_eiffel
            metres >= 100 -> KMR.strings.stats_landmark_field
            metres >= 30 -> KMR.strings.stats_landmark_whale
            else -> null
        }
    }

    /** "1 AM", "9 PM" in the phone's language */
    fun hour(hour: Int, locale: Locale = Locale.getDefault()): String =
        LocalTime.of(hour.coerceIn(0, 23), 0).format(DateTimeFormatter.ofPattern("h a", locale))
}
// KMK <--
