package eu.kanade.tachiyomi.util.system

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: tells whether a foldable is open. Uses the screen's smallest width, so an open
 * foldable (or a tablet) counts as unfolded and a normal phone screen counts as folded.
 */
object EggikuFoldState {

    private const val UNFOLDED_MIN_WIDTH_DP = 600

    fun isUnfolded(): Boolean {
        val configuration = Injekt.get<Application>().resources.configuration
        return configuration.smallestScreenWidthDp >= UNFOLDED_MIN_WIDTH_DP
    }
}
// KMK <--
