package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.drop.EggikuDrops
import eu.kanade.tachiyomi.data.search.EggikuSearchText
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

// KMK -->
/** Everything Eggiku adds to a global search, kept in one place so the upstream state stays small */
@Immutable
data class EggikuSearchState(
    /** A search group id (or [eu.kanade.tachiyomi.data.search.EggikuSearchGroups.FAST]); null = Pinned / All */
    val groupId: String? = null,
    /** Search a genre / tag across sources instead of a title */
    val tagMode: Boolean = false,
    val merged: Boolean = false,
    val altTitlesOn: Boolean = false,
    /** Other names being searched too, once AniList answers */
    val altTitles: List<String> = emptyList(),
    val filters: EggikuSearchFilters = EggikuSearchFilters(),
    /** Left out because they kept failing */
    val skipped: List<Source> = emptyList(),
    /** How long each source took this time, in milliseconds */
    val timings: Map<Long, Long> = emptyMap(),
    /** Chapter counts and status fetched for series (by manga id) */
    val details: Map<Long, EggikuSeriesDetails> = emptyMap(),
    /** Manga ids being compared right now */
    val comparing: Set<Long> = emptySet(),
    val includeMine: Boolean = true,
    val mine: EggikuMine? = null,
)

@Immutable
data class EggikuSearchFilters(
    /** Empty = every language */
    val languages: Set<String> = emptySet(),
    val notInLibrary: Boolean = false,
    /** SManga status values; empty = any. Series whose status isn't known yet are kept */
    val statuses: Set<Long> = emptySet(),
    /** 0 = off. Only applies to series whose chapter count is known */
    val minChapters: Int = 0,
) {
    val active: Boolean get() = languages.isNotEmpty() || notInLibrary || statuses.isNotEmpty() || minChapters > 0
    val count: Int get() = listOf(languages.isNotEmpty(), notInLibrary, statuses.isNotEmpty(), minChapters > 0).count { it }
}

@Immutable
data class EggikuSeriesDetails(
    val chapters: Int,
    /** Newest chapter's upload time, 0 if the source doesn't say */
    val latestUpload: Long,
    val status: Long,
    val failed: Boolean = false,
)

/** Matches from your own stuff */
@Immutable
data class EggikuMine(
    val library: List<Manga> = emptyList(),
    val history: List<Manga> = emptyList(),
    val drops: List<EggikuDrops.Received> = emptyList(),
) {
    val isEmpty: Boolean get() = library.isEmpty() && history.isEmpty() && drops.isEmpty()
}

/** One series found on several sources */
@Immutable
data class EggikuMergedSeries(
    val key: String,
    val title: String,
    val entries: List<Pair<Source, Manga>>,
) {
    val inLibrary: Boolean get() = entries.any { it.second.favorite }
}

/** Thrown for a source that has no filter matching the searched tag */
class EggikuNoTagException : Exception("No matching tag on this source")

/** Thrown when a source takes longer than the time limit */
class EggikuTooSlowException(seconds: Int) : Exception("Took longer than $seconds s")

object EggikuSearchFiltering {

    fun keep(manga: Manga, filters: EggikuSearchFilters, details: Map<Long, EggikuSeriesDetails>): Boolean {
        if (filters.notInLibrary && manga.favorite) return false
        val known = details[manga.id]
        if (filters.statuses.isNotEmpty()) {
            val status = known?.status?.takeIf { it != 0L } ?: manga.status
            if (status != 0L && status !in filters.statuses) return false
        }
        if (filters.minChapters > 0 && known != null && !known.failed && known.chapters < filters.minChapters) return false
        return true
    }

    /** The source rows after Eggiku's filters; tag-less sources are hidden in a tag search */
    fun apply(
        items: Map<Source, SearchItemResult>,
        eggiku: EggikuSearchState,
    ): Map<Source, SearchItemResult> {
        val filters = eggiku.filters
        return items
            .filter { (source, result) ->
                (filters.languages.isEmpty() || source.lang in filters.languages) &&
                    !(eggiku.tagMode && result is SearchItemResult.Error && result.throwable is EggikuNoTagException)
            }
            .mapValues { (_, result) ->
                if (result is SearchItemResult.Success && filters.active) {
                    SearchItemResult.Success(result.result.filter { keep(it, filters, eggiku.details) })
                } else {
                    result
                }
            }
    }

    /** Groups the same series from different sources, biggest groups first */
    fun merge(items: Map<Source, SearchItemResult>): List<EggikuMergedSeries> {
        val groups = LinkedHashMap<String, MutableList<Pair<Source, Manga>>>()
        items.forEach { (source, result) ->
            if (result !is SearchItemResult.Success) return@forEach
            result.result.forEach { manga ->
                val key = EggikuSearchText.titleKey(manga.title).ifEmpty { "${manga.source}:${manga.url}" }
                val list = groups.getOrPut(key) { mutableListOf() }
                // One entry per source per series
                if (list.none { it.first.id == source.id }) list += source to manga
            }
        }
        return groups.map { (key, entries) -> EggikuMergedSeries(key, entries.first().second.title, entries) }
            .sortedWith(compareByDescending<EggikuMergedSeries> { it.inLibrary }.thenByDescending { it.entries.size })
    }

    /** The source to read on: most chapters, then the newest chapter. Null until compared */
    fun best(series: EggikuMergedSeries, details: Map<Long, EggikuSeriesDetails>): Manga? {
        val known = series.entries.mapNotNull { (_, manga) -> details[manga.id]?.takeIf { !it.failed }?.let { manga to it } }
        if (known.isEmpty()) return null
        return known.maxWithOrNull(compareBy<Pair<Manga, EggikuSeriesDetails>> { it.second.chapters }.thenBy { it.second.latestUpload })
            ?.first
    }
}
// KMK <--
