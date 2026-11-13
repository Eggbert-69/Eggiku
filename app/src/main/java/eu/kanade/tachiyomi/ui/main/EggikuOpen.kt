package eu.kanade.tachiyomi.ui.main

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.library.EggikuDeadSeriesScreen
import eu.kanade.tachiyomi.ui.stats.EggikuAchievementsScreen
import eu.kanade.tachiyomi.ui.stats.EggikuRecapScreen
import mihon.feature.migration.config.MigrationConfigScreen

// KMK -->
/** Eggiku: notifications that open a specific screen (migrate a series, a whole source, ...) */
object EggikuOpen {

    const val ACTION = "app.eggiku.OPEN"
    private const val EXTRA_SCREEN = "eggiku_screen"
    private const val EXTRA_ID = "eggiku_id"

    const val MIGRATE_SERIES = "migrate_series"
    const val MIGRATE_SOURCE = "migrate_source"
    const val DEAD_SERIES = "dead_series"
    const val ACHIEVEMENTS = "achievements"
    const val LAST_MONTH_WRAP = "last_month_wrap"

    fun pendingIntent(context: Context, requestCode: Int, screen: String, id: Long = 0L): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_SCREEN, screen)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Returns true when the intent was one of ours */
    fun handle(intent: Intent, navigator: Navigator): Boolean {
        if (intent.action != ACTION) return false
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        navigator.popUntilRoot()
        when (intent.getStringExtra(EXTRA_SCREEN)) {
            MIGRATE_SERIES -> navigator.push(MigrationConfigScreen(listOf(id)))
            MIGRATE_SOURCE -> navigator.push(MigrateMangaScreen(id))
            DEAD_SERIES -> navigator.push(EggikuDeadSeriesScreen())
            ACHIEVEMENTS -> navigator.push(EggikuAchievementsScreen())
            LAST_MONTH_WRAP -> navigator.push(EggikuRecapScreen(startMonthOffset = -1))
        }
        return true
    }
}
// KMK <--
