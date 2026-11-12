package eu.kanade.tachiyomi.data.smartdownload

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.library.EggikuContinueReading
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

// KMK -->
/**
 * Eggiku smart downloads: on Wi-Fi while charging, grab the next few unread chapters of whatever
 * you're in the middle of (your Continue reading row), and clear the ones it downloaded once
 * they've been read and a week has passed. Chapters you downloaded yourself are never touched.
 */
object EggikuSmartDownloads {

    private const val TAG = "EggikuSmartDownloads"
    private const val RECENT_DAYS = 30L
    private const val MAX_SERIES = 10
    private const val KEEP_READ_DAYS = 7L

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun enabled() = preferenceStore.getBoolean("eggiku_smart_downloads", true)

    fun chaptersAhead() = preferenceStore.getInt("eggiku_smart_downloads_ahead", 3)

    fun clearRead() = preferenceStore.getBoolean("eggiku_smart_downloads_clear_read", true)

    /** "chapterId:mangaId:downloadedAtMillis" for chapters this feature downloaded */
    private fun ours() = preferenceStore.getStringSet("eggiku_smart_downloaded", emptySet())

    private data class Ours(val chapterId: Long, val mangaId: Long, val at: Long)

    private fun parse(entries: Set<String>) = entries.mapNotNull {
        val p = it.split(':')
        Ours(
            p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null,
            p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null,
            p.getOrNull(2)?.toLongOrNull() ?: 0L,
        )
    }

    fun setup(context: Context) {
        val wm = context.workManager
        if (!enabled().get()) {
            wm.cancelUniqueWork(TAG)
            return
        }
        val request = PeriodicWorkRequestBuilder<SmartDownloadWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresCharging(true)
                    .build(),
            )
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun run() {
        if (!enabled().get()) return
        val downloadManager = Injekt.get<DownloadManager>()
        val sourceManager = Injekt.get<SourceManager>()
        val getChapters = Injekt.get<GetChaptersByMangaId>()
        val now = System.currentTimeMillis()
        val recorded = parse(ours().get()).toMutableList()

        // 1) Clear what we downloaded, once read and a week old
        if (clearRead().get()) {
            val cutoff = now - TimeUnit.DAYS.toMillis(KEEP_READ_DAYS)
            recorded.filter { it.at < cutoff }.groupBy { it.mangaId }.forEach { (mangaId, entries) ->
                val manga = Injekt.get<GetManga>().await(mangaId) ?: return@forEach
                val source = sourceManager.get(manga.source) ?: return@forEach
                val ids = entries.map { it.chapterId }.toSet()
                val readOnes = getChapters.await(mangaId).filter { it.id in ids && it.read }
                if (readOnes.isNotEmpty()) {
                    downloadManager.deleteChapters(readOnes, manga, source)
                    recorded.removeAll { it.chapterId in readOnes.map(Chapter::id) }
                }
            }
        }

        // 2) Download the next chapters of what you're reading now
        val skipped = EggikuContinueReading.skippedIds(now)
        val recentCutoff = now - TimeUnit.DAYS.toMillis(RECENT_DAYS)
        val reading = Injekt.get<GetLibraryManga>().await()
            .distinctBy { it.id }
            .filter { it.lastRead >= recentCutoff && it.unreadCount > 0 && it.id !in skipped }
            .sortedByDescending { it.lastRead }
            .take(MAX_SERIES)
        val ahead = chaptersAhead().get().coerceIn(1, 10)
        reading.forEach { item ->
            val manga = item.manga
            val chapters = getChapters.await(manga.id, applyFilter = true)
                .filter { !it.read }
                .sortedBy { it.chapterNumber }
                .filter {
                    !downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.ogTitle, manga.source)
                }
                .take(ahead)
            if (chapters.isNotEmpty()) {
                downloadManager.downloadChapters(manga, chapters)
                chapters.forEach { recorded += Ours(it.id, manga.id, now) }
            }
        }
        ours().set(recorded.map { "${it.chapterId}:${it.mangaId}:${it.at}" }.toSet())
    }

    class SmartDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                run()
                Result.success()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Eggiku smart downloads failed" }
                Result.success()
            }
        }
    }
}
// KMK <--
