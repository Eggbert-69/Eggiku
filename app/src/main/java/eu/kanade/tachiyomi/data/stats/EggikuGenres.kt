package eu.kanade.tachiyomi.data.stats

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetReadMangaNotInLibraryView
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Eggiku: which genres you actually read, by chapters read, for any stretch of time */
object EggikuGenres {

    data class Share(val genre: String, val chapters: Int, val percent: Float)

    /** Genres that say little about what a series is like */
    private val ignored = setOf("manga", "manhwa", "manhua", "webtoon", "webtoons", "comic", "comics", "full color", "long strip")

    /** [from]/[to] are epoch millis; null means no limit */
    suspend fun shares(from: Long? = null, to: Long? = null, limit: Int = 6): List<Share> {
        val everything = (Injekt.get<GetLibraryManga>().await() + Injekt.get<GetReadMangaNotInLibraryView>().await())
            .distinctBy { it.id }
        val genresById = everything.associate { item ->
            item.id to item.manga.genre.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.lowercase() !in ignored }
                .map { g -> g.lowercase().replaceFirstChar { it.titlecase() } }
                .distinct()
        }
        val counts = HashMap<String, Int>()
        Injekt.get<DatabaseHandler>().awaitList {
            historyQueries.eggikuReadEvents { mangaId, lastRead, _ -> mangaId to (lastRead?.time ?: 0L) }
        }.forEach { (mangaId, at) ->
            if (at <= 0 || (from != null && at < from) || (to != null && at > to)) return@forEach
            genresById[mangaId]?.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val total = counts.values.sum().coerceAtLeast(1)
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Share(it.key, it.value, it.value * 100f / total) }
    }
}
// KMK <--
