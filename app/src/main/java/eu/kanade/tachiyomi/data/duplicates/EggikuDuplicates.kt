package eu.kanade.tachiyomi.data.duplicates

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku duplicate skimmer: finds library series with the same name, keeps the one you've read
 * the most of, copies your progress, bookmarks and categories onto it, then takes the others out
 * of the library. Nothing is ever deleted outright, and a merge can be undone for a week.
 */
object EggikuDuplicates {

    data class Group(
        val key: String,
        val entries: List<LibraryManga>,
    ) {
        /** Most read chapters, then most chapters, then most recently updated */
        val suggestedKeeper: LibraryManga
            get() = entries.sortedWith(
                compareByDescending<LibraryManga> { it.readCount }
                    .thenByDescending { it.totalChapters }
                    .thenByDescending { it.manga.lastUpdate },
            ).first()
    }

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Entries look like "removedMangaId:mergedAtMillis" */
    private fun removed() = preferenceStore.getStringSet("eggiku_duplicates_removed", emptySet())

    private const val UNDO_MS = 7L * 24 * 60 * 60 * 1000

    /** "The Hero's Return (Official)" and "the heros return" count as the same name */
    fun normalize(title: String): String = title
        .lowercase()
        .replace(Regex("""\(.*?\)|\[.*?\]|\{.*?\}"""), " ")
        .replace(Regex("""\bofficial\b|\bcolou?red\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), "")
        .trim()

    suspend fun findGroups(): List<Group> = withIOContext {
        Injekt.get<GetLibraryManga>().await()
            .distinctBy { it.id }
            .groupBy { normalize(it.manga.title) }
            .filter { (key, entries) -> key.isNotEmpty() && entries.size > 1 }
            .map { (key, entries) -> Group(key, entries) }
            .sortedBy { it.entries.first().manga.title.lowercase() }
    }

    /** Merges each group into its keeper; returns how many series were taken out of the library */
    suspend fun merge(choices: List<Pair<LibraryManga, List<LibraryManga>>>): Int = withIOContext {
        val getChapters = Injekt.get<GetChaptersByMangaId>()
        val updateChapter = Injekt.get<UpdateChapter>()
        val setCategories = Injekt.get<SetMangaCategories>()
        val updateManga = Injekt.get<UpdateManga>()
        val now = System.currentTimeMillis()
        var removedCount = 0
        val removedIds = mutableListOf<Long>()

        choices.forEach { (keeper, others) ->
            val keeperChapters = getChapters.await(keeper.id)
            val byNumber = keeperChapters.filter { it.chapterNumber >= 0 }.groupBy { it.chapterNumber }
            val updates = mutableMapOf<Long, ChapterUpdate>()
            others.forEach { other ->
                getChapters.await(other.id).forEach { chapter ->
                    if (!chapter.read && !chapter.bookmark) return@forEach
                    byNumber[chapter.chapterNumber]?.forEach { target ->
                        val existing = updates[target.id] ?: ChapterUpdate(id = target.id)
                        updates[target.id] = existing.copy(
                            read = if (chapter.read && !target.read) true else existing.read,
                            bookmark = if (chapter.bookmark && !target.bookmark) true else existing.bookmark,
                        )
                    }
                }
            }
            if (updates.isNotEmpty()) updateChapter.awaitAll(updates.values.toList())

            val categories = (keeper.categories + others.flatMap { it.categories }).filter { it != 0L }.distinct()
            if (categories.isNotEmpty()) setCategories.await(keeper.id, categories)

            EggikuAchievements.bump(EggikuAchievements.COUNT_MERGES)
            others.forEach { other ->
                EggikuRatings.move(other.id, keeper.id)
                if (updateManga.awaitUpdateFavorite(other.id, false)) {
                    removedCount++
                    removedIds += other.id
                }
            }
        }
        val keep = removed().get().filter { entry ->
            (entry.substringAfter(':').toLongOrNull() ?: 0L) > now - UNDO_MS
        }.toSet()
        removed().set(keep + removedIds.map { "$it:$now" })
        removedCount
    }

    /** Series taken out by a merge in the last week */
    fun undoable(now: Long = System.currentTimeMillis()): List<Long> = removed().get().mapNotNull { entry ->
        val at = entry.substringAfter(':').toLongOrNull() ?: return@mapNotNull null
        if (now - at > UNDO_MS) null else entry.substringBefore(':').toLongOrNull()
    }

    /** Puts the most recently merged-away series back in the library */
    suspend fun undoLast(): Int = withIOContext {
        val entries = removed().get().mapNotNull { entry ->
            val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val at = entry.substringAfter(':').toLongOrNull() ?: return@mapNotNull null
            id to at
        }
        val lastAt = entries.maxOfOrNull { it.second } ?: return@withIOContext 0
        if (System.currentTimeMillis() - lastAt > UNDO_MS) return@withIOContext 0
        val toRestore = entries.filter { it.second == lastAt }.map { it.first }
        val updateManga = Injekt.get<UpdateManga>()
        toRestore.forEach { updateManga.awaitUpdateFavorite(it, true) }
        removed().set(entries.filter { it.second != lastAt }.map { "${it.first}:${it.second}" }.toSet())
        toRestore.size
    }
}
// KMK <--
