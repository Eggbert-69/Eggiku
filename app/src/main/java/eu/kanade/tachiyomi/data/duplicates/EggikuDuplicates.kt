package eu.kanade.tachiyomi.data.duplicates

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.search.EggikuAltTitles
import eu.kanade.tachiyomi.data.shelves.EggikuShelves
import eu.kanade.tachiyomi.data.tags.EggikuTags
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.interactor.GetTracks
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
        /**
         * Most read chapters; then the copy that's further along (more chapters); then the one
         * that gets new chapters sooner (newest upload, then most recently updated)
         */
        val suggestedKeeper: LibraryManga
            get() = entries.sortedWith(
                compareByDescending<LibraryManga> { it.readCount }
                    .thenByDescending { it.totalChapters }
                    .thenByDescending { it.latestUpload }
                    .thenByDescending { it.manga.lastUpdate },
            ).first()

        /** Why these were grouped, for the screen */
        var matchedBy: MatchedBy = MatchedBy.NAME
            internal set
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

    enum class MatchedBy { NAME, OTHER_NAME, TRACKER }

    /** "mangaId|normalized other name", filled in by [lookUpOtherNames] */
    private fun otherNamesPref() = preferenceStore.getStringSet("eggiku_duplicates_other_names", emptySet())

    /** Series already looked up on AniList, so a second run only does new ones */
    private fun lookedUpPref() = preferenceStore.getStringSet("eggiku_duplicates_looked_up", emptySet())

    /**
     * Groups series that share a name, one of their other names (English, romaji, native, from
     * AniList), or the same tracker entry. Anything linked by any of those ends up in one group.
     */
    suspend fun findGroups(): List<Group> = withIOContext {
        val library = Injekt.get<GetLibraryManga>().await().distinctBy { it.id }
        val byId = library.associateBy { it.id }
        val parent = library.associate { it.id to it.id }.toMutableMap()
        fun find(id: Long): Long {
            var root = id
            while (parent[root] != root) root = parent[root]!!
            parent[id] = root
            return root
        }
        val how = mutableMapOf<Long, MatchedBy>()
        fun join(ids: List<Long>, reason: MatchedBy) {
            val distinct = ids.distinct()
            if (distinct.size < 2) return
            val first = find(distinct.first())
            distinct.drop(1).forEach { other ->
                val root = find(other)
                if (root != first) {
                    parent[root] = first
                    how[first] = listOfNotNull(how[first], how[root], reason).minBy { it.ordinal }
                }
            }
        }

        // Same name
        library.groupBy { normalize(it.manga.title) }
            .filterKeys { it.isNotEmpty() }
            .values
            .forEach { join(it.map { m -> m.id }, MatchedBy.NAME) }

        // A name of one is another name of the other
        val otherNames = otherNamesPref().get().mapNotNull { entry ->
            val id = entry.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
            if (id !in byId) return@mapNotNull null
            id to entry.substringAfter('|')
        }
        val names = (library.map { it.id to normalize(it.manga.title) } + otherNames)
            .filter { it.second.length >= MIN_NAME }
        names.groupBy({ it.second }, { it.first }).values.forEach { join(it, MatchedBy.OTHER_NAME) }

        // Tracked to the same entry on the same tracker
        try {
            Injekt.get<GetTracks>().await()
                .filter { it.mangaId in byId && it.remoteId > 0 }
                .groupBy({ "${it.trackerId}:${it.remoteId}" }, { it.mangaId })
                .values
                .forEach { join(it, MatchedBy.TRACKER) }
        } catch (_: Exception) {
            // Tracks are a bonus
        }

        library.groupBy { find(it.id) }
            .filterValues { it.size > 1 }
            .map { (root, entries) ->
                val sorted = entries.sortedBy { it.manga.title.lowercase() }
                Group(normalize(sorted.first().manga.title).ifEmpty { "id$root" } + "#$root", sorted).apply {
                    matchedBy = how[root] ?: MatchedBy.NAME
                }
            }
            .sortedBy { it.entries.first().manga.title.lowercase() }
    }

    /**
     * Looks up other names on AniList for series that haven't been looked up yet, a batch at a
     * time so it doesn't hammer AniList. Returns how many were looked up.
     */
    suspend fun lookUpOtherNames(max: Int = LOOKUP_BATCH): Int = withIOContext {
        val done = lookedUpPref().get()
        val todo = Injekt.get<GetLibraryManga>().await()
            .distinctBy { it.id }
            .filter { it.id.toString() !in done }
            .take(max)
        val found = mutableSetOf<String>()
        val looked = mutableSetOf<String>()
        for (series in todo) {
            val names = EggikuAltTitles.find(series.manga.title, max = 6)
            names.map { normalize(it) }.filter { it.length >= MIN_NAME }.forEach { found += "${series.id}|$it" }
            looked += series.id.toString()
            // AniList allows about 30 requests a minute
            delay(2_100)
        }
        otherNamesPref().set(otherNamesPref().get() + found)
        lookedUpPref().set(done + looked)
        looked.size
    }

    /** Series not looked up on AniList yet */
    suspend fun notLookedUpCount(): Int = withIOContext {
        val done = lookedUpPref().get()
        Injekt.get<GetLibraryManga>().await().distinctBy { it.id }.count { it.id.toString() !in done }
    }

    private const val MIN_NAME = 3
    private const val LOOKUP_BATCH = 25

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
                EggikuTags.move(other.id, keeper.id)
                EggikuShelves.move(other.id, keeper.id)
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
