package eu.kanade.domain.ui.model

import androidx.appcompat.app.AppCompatDelegate
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalTime

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,

    // KMK -->
    /** Dark between the user's chosen evening and morning hours */
    SCHEDULED,
    // KMK <--
}

fun setAppCompatDelegateThemeMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
        when (themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            // KMK -->
            ThemeMode.SCHEDULED -> if (isScheduledDarkNow()) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            // KMK <--
        },
    )
}

// KMK -->
fun isScheduledDarkNow(): Boolean {
    val prefs = Injekt.get<UiPreferences>()
    val start = prefs.darkScheduleStartHour().get()
    val end = prefs.darkScheduleEndHour().get()
    val hour = LocalTime.now().hour
    return when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }
}
// KMK <--
