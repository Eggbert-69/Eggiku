package eu.kanade.tachiyomi.data.stats

import eu.kanade.tachiyomi.extension.EggikuNsfwOverride
import eu.kanade.tachiyomi.extension.ExtensionManager
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

    /** Adult tags never show up in genre stats */
    private val adultWords = listOf(
        "adult", "mature", "smut", "hentai", "ecchi", "erotic", "sexual", "sex", "nsfw", "18+", "r-18", "r18",
        "explicit", "lewd", "porn", "nude", "nudity", "yaoi", "yuri", "doujin", "ahegao", "milf",
    )

    private fun isAdult(genre: String): Boolean {
        val g = genre.lowercase()
        return adultWords.any { g.contains(it) }
    }

    /** Sources from extensions marked 18+ (counting your own overrides) */
    private fun adultSourceIds(): Set<Long> = try {
        val overrides = EggikuNsfwOverride.overrides().get()
        Injekt.get<ExtensionManager>().installedExtensionsFlow.value
            .filter { EggikuNsfwOverride.isNsfw(it, overrides) }
            .flatMap { ext -> ext.sources.map { it.id } }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /** [from]/[to] are epoch millis; null means no limit */
    suspend fun shares(from: Long? = null, to: Long? = null, limit: Int = 6): List<Share> {
        val adultSources = adultSourceIds()
        val everything = (Injekt.get<GetLibraryManga>().await() + Injekt.get<GetReadMangaNotInLibraryView>().await())
            .distinctBy { it.id }
            // Series from 18+ sources are left out of genre stats entirely
            .filterNot { it.manga.source in adultSources }
        val genresById = everything.associate { item ->
            item.id to item.manga.genre.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.lowercase() !in ignored && !isAdult(it) }
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
