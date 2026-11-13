package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.tags.EggikuTags
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import java.time.Instant

class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val coverCache: CoverCache,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    // KMK -->
    private val getHistory: GetHistory,
    private val upsertHistory: UpsertHistory,
    // KMK <--
) {
    private val enhancedServices by lazy { trackerManager.trackers.filterIsInstance<EnhancedTracker>() }

    suspend operator fun invoke(
        current: Manga,
        target: Manga,
        replace: Boolean,
        // KMK -->
        presetFlags: Set<MigrationFlag>? = null,
        // KMK <--
        // SY -->
        throttleFunc: suspend () -> Unit = {},
        // SY <--
    ) {
        val targetSource = sourceManager.get(target.source) ?: return
        val currentSource = sourceManager.get(current.source)
        val flags = /* KMK --> */ presetFlags ?: /* KMK <-- */ sourcePreferences.migrationFlags().get()
        // KMK -->
        // For undo: what things looked like before
        val newWasFavorite = target.favorite
        val oldCategoriesBefore = try {
            getCategories.await(current.id).map { it.id }.filter { it != 0L }
        } catch (_: Exception) {
            emptyList()
        }
        // KMK <--

        try {
            updateMangaFromRemote(
                manga = target,
                fetchChapters = true,
                // SY -->
                throttleFunc = throttleFunc,
                // SY <--
            ).getOrThrow()

            // Update chapters read, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                // KMK -->
                // Some sources number chapters differently (one starts at 0, the other at 1), so
                // line them up before copying read progress across
                val offset = EggikuMigrationSmarts.chapterOffset(prevMangaChapters, mangaChapters)
                if (offset != 0.0) EggikuMigrationSmarts.offsetsApplied.incrementAndGet()
                // A series that had gone a year without chapters, moved somewhere with more of them
                val lastOld = prevMangaChapters.maxOfOrNull { it.dateUpload } ?: 0L
                val yearAgo = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
                if (lastOld in 1..<yearAgo && mangaChapters.size > prevMangaChapters.size) {
                    EggikuAchievements.bump(EggikuAchievements.COUNT_REVIVED)
                }
                // KMK <--
                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }
                    // KMK -->
                    ?.plus(offset)
                // KMK <--

                // SY -->
                val historyUpdates = mutableListOf<HistoryUpdate>()
                val prevHistoryList = getHistory.await(current.id)
                    // SY <--
                    // KMK -->
                    .associateBy { it.chapterId }
                // KMK <--

                val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                    var updatedChapter = mangaChapter
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find {
                                it.isRecognizedNumber &&
                                    // KMK -->
                                    it.chapterNumber + offset == updatedChapter.chapterNumber
                                // KMK <--
                            }

                        if (prevChapter != null) {
                            updatedChapter = updatedChapter.copy(
                                // SY -->
                                // If chapters match then mark new manga's chapters read/unread as old one
                                read = prevChapter.read,
                                // SY <--
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                                lastPageRead = prevChapter.lastPageRead,
                            )
                            // SY -->
                            // KMK -->
                            prevHistoryList[prevChapter.id]?.let { prevHistory ->
                                // KMK <--
                                historyUpdates += HistoryUpdate(
                                    mangaChapter.id,
                                    prevHistory.readAt ?: return@let,
                                    prevHistory.readDuration,
                                )
                            }
                            // SY <--
                        }
                        // KMK -->
                        // If chapters which only present on new manga then mark read up to latest read chapter number
                        else /* KMK <-- */ if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            updatedChapter = updatedChapter.copy(read = true)
                        }
                    }

                    updatedChapter
                }

                val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
                updateChapter.awaitAll(chapterUpdates)
                // SY -->
                upsertHistory.awaitAll(historyUpdates)
                // SY <--
            }

            // KMK -->
            // Read the categories now, before anything changes the old entry, and apply them
            // after the target is in the library (see below) so nothing can undo them
            val categoryIds = if (MigrationFlag.CATEGORY in flags) {
                getCategories.await(current.id).map { it.id }
            } else {
                null
            }
            // KMK <--

            // Update track
            // SY -->
            if (MigrationFlag.TRACK in flags) {
                // SY <--
                getTracks.await(current.id).mapNotNull { track ->
                    val updatedTrack = track.copy(mangaId = target.id)

                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                    if (service != null) {
                        service.migrateTrack(updatedTrack, target, targetSource)
                    } else {
                        updatedTrack
                    }
                }
                    .takeIf { it.isNotEmpty() }
                    ?.let { insertTrack.awaitAll(it) }
            }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                downloadManager.deleteManga(current, currentSource)
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover()) {
                coverCache.setCustomCoverToCache(target, coverCache.getCustomCoverFile(current.id).inputStream())
            }

            val currentMangaUpdate = MangaUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            val targetMangaUpdate = MangaUpdate(
                id = target.id,
                favorite = true,
                chapterFlags = current.chapterFlags
                    // KMK -->
                    .takeIf { MigrationFlag.EXTRA in flags },
                // KMK <--
                viewerFlags = current.viewerFlags
                    // KMK -->
                    .takeIf { MigrationFlag.EXTRA in flags },
                // KMK <--
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))

            // KMK -->
            EggikuRatings.move(current.id, target.id)
            EggikuTags.move(current.id, target.id)
            EggikuAchievements.bump(EggikuAchievements.COUNT_MIGRATED)
            EggikuMigrationUndo.record(
                oldId = current.id,
                newId = target.id,
                replaced = replace,
                newWasFavorite = newWasFavorite,
                oldCategories = oldCategoriesBefore,
            )
            // Eggiku: put the migrated entry in the same categories, then check it stuck
            if (categoryIds != null) {
                setMangaCategories.await(target.id, categoryIds)
                val applied = getCategories.await(target.id).map { it.id }.toSet()
                if (applied != categoryIds.toSet()) {
                    logcat(LogPriority.WARN) { "Eggiku: categories didn't stick after migration, retrying" }
                    setMangaCategories.await(target.id, categoryIds)
                }
            }
            // KMK <--
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            // KMK -->
            logcat(LogPriority.ERROR, e) { "Eggiku: migration of ${current.title} failed" }
            // KMK <--
        }
    }
}
