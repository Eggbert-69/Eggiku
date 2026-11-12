package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import kotlin.time.Duration.Companion.days

// KMK -->

/**
 * Eggiku's extra library filters, kept apart from the upstream filter plumbing.
 */
data class EggikuLibraryFilters(
    val ongoing: TriState = TriState.DISABLED,
    val pausedOrCancelled: TriState = TriState.DISABLED,
    val missingSource: TriState = TriState.DISABLED,
    val notReadRecently: TriState = TriState.DISABLED,
    val noNewChapters: TriState = TriState.DISABLED,
    val includedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = listOf(ongoing, pausedOrCancelled, missingSource, notReadRecently, noNewChapters)
            .any { it != TriState.DISABLED } ||
            includedGenres.isNotEmpty() ||
            excludedGenres.isNotEmpty()

    companion object {
        val NOT_READ_RECENTLY = 30.days
        val NO_NEW_CHAPTERS = 180.days
    }
}

fun LibraryPreferences.eggikuFiltersFlow(): Flow<EggikuLibraryFilters> {
    return combine(
        filterOngoing().changes(),
        filterPausedOrCancelled().changes(),
        filterMissingSource().changes(),
        filterNotReadRecently().changes(),
        filterNoNewChapters().changes(),
        filterGenresInclude().changes(),
        filterGenresExclude().changes(),
    ) { values ->
        EggikuLibraryFilters(
            ongoing = values[0] as TriState,
            pausedOrCancelled = values[1] as TriState,
            missingSource = values[2] as TriState,
            notReadRecently = values[3] as TriState,
            noNewChapters = values[4] as TriState,
            includedGenres = (values[5] as Set<*>).mapTo(mutableSetOf()) { it.toString().lowercase() },
            excludedGenres = (values[6] as Set<*>).mapTo(mutableSetOf()) { it.toString().lowercase() },
        )
    }
        .distinctUntilChanged()
}

fun List<LibraryItem>.applyEggikuFilters(
    filters: EggikuLibraryFilters,
    sourceManager: SourceManager,
    now: Long = System.currentTimeMillis(),
): List<LibraryItem> {
    if (!filters.isActive) return this

    val readCutoff = now - EggikuLibraryFilters.NOT_READ_RECENTLY.inWholeMilliseconds
    val uploadCutoff = now - EggikuLibraryFilters.NO_NEW_CHAPTERS.inWholeMilliseconds

    return filter { item ->
        val libraryManga = item.libraryManga
        val manga = libraryManga.manga
        val status = manga.status.toInt()

        applyFilter(filters.ongoing) { status == SManga.ONGOING } &&
            applyFilter(filters.pausedOrCancelled) { status == SManga.ON_HIATUS || status == SManga.CANCELLED } &&
            applyFilter(filters.missingSource) { !manga.isLocal() && sourceManager.get(manga.source) == null } &&
            applyFilter(filters.notReadRecently) { libraryManga.lastRead < readCutoff } &&
            applyFilter(filters.noNewChapters) {
                libraryManga.latestUpload > 0 && libraryManga.latestUpload < uploadCutoff
            } &&
            genresMatch(manga.genre, filters.includedGenres, filters.excludedGenres)
    }
}

private fun genresMatch(genres: List<String>?, included: Set<String>, excluded: Set<String>): Boolean {
    if (included.isEmpty() && excluded.isEmpty()) return true
    val mangaGenres = genres.orEmpty().mapTo(mutableSetOf()) { it.trim().lowercase() }
    if (excluded.any { it in mangaGenres }) return false
    return included.all { it in mangaGenres }
}
// KMK <--
