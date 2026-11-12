package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class LibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayMode: SetDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    // KMK -->
    getLibraryManga: GetLibraryManga = Injekt.get(),
    // KMK <--
) : ScreenModel {

    // KMK -->
    /** Every genre used in the library, for the genre filter */
    val libraryGenresFlow = getLibraryManga.subscribe()
        .map { list ->
            list.asSequence()
                .flatMap { it.manga.genre.orEmpty() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
                .toList()
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    /** Cycles a genre through include, exclude and off */
    fun toggleGenre(genre: String) {
        val key = genre.lowercase()
        val include = libraryPreferences.filterGenresInclude()
        val exclude = libraryPreferences.filterGenresExclude()
        when {
            include.get().any { it.lowercase() == key } -> {
                include.set(include.get().filterNot { it.lowercase() == key }.toSet())
                exclude.set(exclude.get() + genre)
            }
            exclude.get().any { it.lowercase() == key } -> {
                exclude.set(exclude.get().filterNot { it.lowercase() == key }.toSet())
            }
            else -> include.set(include.get() + genre)
        }
    }

    fun clearGenres() {
        libraryPreferences.filterGenresInclude().set(emptySet())
        libraryPreferences.filterGenresExclude().set(emptySet())
    }
    // KMK <--

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    // SY -->
    val grouping by libraryPreferences.groupLibraryBy().asState(screenModelScope)

    // SY <--
    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    // SY -->
    fun setGrouping(grouping: Int) {
        screenModelScope.launchIO {
            libraryPreferences.groupLibraryBy().set(grouping)
        }
    }
    // SY <--
}
