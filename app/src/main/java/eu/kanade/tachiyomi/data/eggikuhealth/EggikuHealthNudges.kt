package eu.kanade.tachiyomi.data.eggikuhealth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.EggikuSourceHealth
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.EggikuOpen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku: when one series keeps failing to update (3 updates in a row), or a whole source has been
 * failing for 3 days, offer to move it somewhere else. Each one is only brought up once.
 */
object EggikuHealthNudges {

    private const val ID_SERIES = -1910
    private const val ID_SOURCE = -1911
    private const val SERIES_FAILS_BEFORE_NUDGE = 3
    private const val SOURCE_DAYS_BEFORE_NUDGE = 3L

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** "mangaId:consecutiveFailures:notified(0/1)" */
    private fun seriesFailures() =
        preferenceStore.getStringSet(Preference.appStateKey("eggiku_series_failures"), emptySet())

    private fun notifiedSources() =
        preferenceStore.getStringSet(Preference.appStateKey("eggiku_nudged_sources"), emptySet())

    private data class Entry(val count: Int, val notified: Boolean)

    private fun parse(entries: Set<String>): MutableMap<Long, Entry> = entries.mapNotNull {
        val parts = it.split(':')
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        id to Entry(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2) == "1")
    }.toMap().toMutableMap()

    /** [results] holds one (manga, succeeded) pair per entry that was updated */
    fun record(context: Context, results: List<Pair<Manga, Boolean>>) {
        if (results.isEmpty()) return
        val current = parse(seriesFailures().get())
        val newlyFailing = mutableListOf<Manga>()
        results.forEach { (manga, ok) ->
            if (ok) {
                current.remove(manga.id)
            } else {
                val before = current[manga.id] ?: Entry(0, false)
                val now = before.copy(count = before.count + 1)
                if (now.count >= SERIES_FAILS_BEFORE_NUDGE && !now.notified) {
                    newlyFailing += manga
                    current[manga.id] = now.copy(notified = true)
                } else {
                    current[manga.id] = now
                }
            }
        }
        seriesFailures().set(current.map { (id, e) -> "$id:${e.count}:${if (e.notified) 1 else 0}" }.toSet())

        if (newlyFailing.isNotEmpty()) notifySeries(context, newlyFailing)
        checkSources(context)
    }

    private fun notifySeries(context: Context, failing: List<Manga>) {
        val single = failing.singleOrNull()
        val intent = if (single != null) {
            EggikuOpen.pendingIntent(context, ID_SERIES, EggikuOpen.MIGRATE_SERIES, single.id)
        } else {
            PendingIntent.getActivity(
                context,
                ID_SERIES,
                Intent(context, MainActivity::class.java)
                    .setAction(Constants.SHORTCUT_LIBRARY_UPDATE_ERRORS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        context.notify(ID_SERIES, Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            if (single != null) {
                setContentTitle(context.stringResource(KMR.strings.eggiku_nudge_series_title, single.title))
                setContentText(context.stringResource(KMR.strings.eggiku_nudge_series_text))
            } else {
                setContentTitle(
                    context.pluralStringResource(KMR.plurals.eggiku_nudge_many_series, failing.size, failing.size),
                )
                setContentText(context.stringResource(KMR.strings.eggiku_nudge_many_series_text))
            }
            setContentIntent(intent)
            setAutoCancel(true)
        }
    }

    private fun checkSources(context: Context) {
        val now = System.currentTimeMillis()
        val failing = EggikuSourceHealth.parse(EggikuSourceHealth.failing().get())
        val notified = notifiedSources().get()
        // Forget sources that recovered, so a later breakdown gets its own nudge
        val stillNotified = notified.filter { it.toLongOrNull() in failing.keys }.toMutableSet()
        val due = failing.filter { (id, since) ->
            id.toString() !in stillNotified && now - since >= SOURCE_DAYS_BEFORE_NUDGE * 24 * 60 * 60 * 1000
        }
        due.keys.firstOrNull()?.let { sourceId ->
            val name = Injekt.get<SourceManager>().getOrStub(sourceId).name
            context.notify(ID_SOURCE, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(context.stringResource(KMR.strings.eggiku_nudge_source_title, name))
                setContentText(context.stringResource(KMR.strings.eggiku_nudge_source_text))
                setContentIntent(EggikuOpen.pendingIntent(context, ID_SOURCE, EggikuOpen.MIGRATE_SOURCE, sourceId))
                setAutoCancel(true)
            }
        }
        stillNotified += due.keys.map { it.toString() }
        notifiedSources().set(stillNotified)
    }
}
// KMK <--
