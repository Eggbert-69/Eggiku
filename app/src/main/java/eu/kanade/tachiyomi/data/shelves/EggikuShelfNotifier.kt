package eu.kanade.tachiyomi.data.shelves

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.duplicates.EggikuDuplicates
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.main.EggikuOpen
import eu.kanade.tachiyomi.util.system.notify
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.delay
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

// KMK -->

/**
 * Eggiku shelf news, after library updates: series on your waiting list that finished, and dropped
 * series that finished or grew a lot since you dropped them. Once a week it also looks, in each
 * favourite's own source, for spin-offs and side stories you don't have yet.
 */
object EggikuShelfNotifier {

    private const val ID_FINISHED = -1920
    private const val ID_SECOND_CHANCE = -1921
    private const val ID_SPINOFFS = -1922

    /** New chapters since dropping that count as "grew a lot" */
    const val GREW_BY = 20L

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private fun lastSpinoffScan() = preferenceStore.getLong(Preference.appStateKey("eggiku_spinoff_scan"), 0L)

    suspend fun afterLibraryUpdate(context: Context) = withIOContext {
        val store = EggikuShelves.store()
        if (store.waiting.isEmpty() && store.dropped.isEmpty()) return@withIOContext
        val library = Injekt.get<GetLibraryManga>().await().associateBy { it.id }

        val finished = store.waiting.keys
            .filter { it !in store.announced }
            .mapNotNull { library[it] }
            .filter { EggikuShelves.isFinished(it.manga.status) }
        val secondChance = store.dropped
            .filter { (id, _) -> id !in store.nudged }
            .mapNotNull { (id, drop) ->
                val manga = library[id] ?: return@mapNotNull null
                val nowFinished = EggikuShelves.isFinished(manga.manga.status) && !EggikuShelves.isFinished(drop.statusAt)
                val grew = drop.chaptersAt > 0 && manga.totalChapters - drop.chaptersAt >= GREW_BY
                manga.takeIf { nowFinished || grew }
            }
        if (finished.isEmpty() && secondChance.isEmpty()) return@withIOContext

        EggikuShelves.update {
            it.copy(announced = it.announced + finished.map { m -> m.id }, nudged = it.nudged + secondChance.map { m -> m.id })
        }

        if (finished.isNotEmpty()) {
            val perChapter = EggikuShelves.secondsPerChapter()
            val text = if (finished.size == 1) {
                val manga = finished.first()
                context.stringResource(
                    KMR.strings.shelf_finished_one,
                    manga.manga.title,
                    EggikuShelves.formatTime(context, EggikuShelves.bingeSeconds(manga.unreadCount, perChapter)),
                )
            } else {
                finished.joinToString { it.manga.title }
            }
            context.notify(ID_FINISHED, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(context.pluralStringResource(KMR.plurals.shelf_finished_title, finished.size, finished.size))
                setContentText(text)
                setStyle(NotificationCompat.BigTextStyle().bigText(text))
                setContentIntent(
                    if (finished.size == 1) {
                        EggikuOpen.pendingIntent(context, ID_FINISHED, EggikuOpen.SERIES, finished.first().id)
                    } else {
                        EggikuOpen.pendingIntent(context, ID_FINISHED, EggikuOpen.SHELVES, EggikuOpen.SHELF_WAITING)
                    },
                )
                setAutoCancel(true)
            }
        }
        if (secondChance.isNotEmpty()) {
            val text = secondChance.joinToString { it.manga.title }
            context.notify(ID_SECOND_CHANCE, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(context.stringResource(KMR.strings.shelf_second_chance_title))
                setContentText(text)
                setStyle(NotificationCompat.BigTextStyle().bigText(text))
                setContentIntent(EggikuOpen.pendingIntent(context, ID_SECOND_CHANCE, EggikuOpen.SHELVES, EggikuOpen.SHELF_DROPPED))
                setAutoCancel(true)
            }
        }
    }

    /** Weekly: looks for spin-offs of what you read most, one search per series in its own source */
    suspend fun scanForSpinoffs(context: Context, force: Boolean = false): Int = withIOContext {
        val now = System.currentTimeMillis()
        if (!force && now - lastSpinoffScan().get() < TimeUnit.DAYS.toMillis(7)) return@withIOContext 0
        lastSpinoffScan().set(now)

        val sourceManager = Injekt.get<SourceManager>()
        val library = Injekt.get<GetLibraryManga>().await().distinctBy { it.id }
        val libraryKeys = library.map { EggikuDuplicates.normalize(it.manga.title) }.toSet()
        val libraryUrls = library.map { "${it.manga.source}|${it.manga.url}" }.toSet()
        val store = EggikuShelves.store()
        val candidates = library
            .filter { it.readCount >= MIN_READ && !it.manga.isLocal() && it.manga.source != MERGED_SOURCE_ID }
            .filter { it.id !in store.dropped }
            .sortedByDescending { it.lastRead }
            .take(MAX_SERIES)

        val found = mutableListOf<EggikuShelves.Found>()
        for (series in candidates) {
            val source = sourceManager.get(series.manga.source) as? CatalogueSource ?: continue
            val base = EggikuDuplicates.normalize(series.manga.title)
            if (base.length < MIN_TITLE) continue
            val results = try {
                source.getSearchManga(1, series.manga.title, source.getFilterList()).mangas
            } catch (_: Exception) {
                continue
            }
            results.take(MAX_RESULTS).forEach { result ->
                val key = EggikuDuplicates.normalize(result.title)
                val entry = EggikuShelves.Found(
                    parentId = series.id,
                    sourceId = source.id,
                    url = result.url,
                    title = result.title,
                    thumbnail = result.thumbnail_url,
                    at = now,
                )
                val isSpinoff = key != base && key.contains(base) && key !in libraryKeys
                if (isSpinoff && entry.key !in store.seen && entry.key !in libraryUrls && found.none { it.key == entry.key }) {
                    found += entry
                }
            }
            // Go easy on sources
            delay(1_500)
        }
        if (found.isEmpty()) return@withIOContext 0

        EggikuShelves.update { it.copy(found = it.found + found, seen = it.seen + found.map { f -> f.key }) }
        val text = found.take(3).joinToString { it.title }
        context.notify(ID_SPINOFFS, Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setContentTitle(context.pluralStringResource(KMR.plurals.shelf_spinoffs_title, found.size, found.size))
            setContentText(text)
            setContentIntent(EggikuOpen.pendingIntent(context, ID_SPINOFFS, EggikuOpen.SHELVES, EggikuOpen.SHELF_SPINOFFS))
            setAutoCancel(true)
        }
        found.size
    }

    private const val MIN_READ = 5L
    private const val MAX_SERIES = 10
    private const val MAX_RESULTS = 20
    private const val MIN_TITLE = 4
}
// KMK <--
