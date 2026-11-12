package eu.kanade.tachiyomi.ui.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.service.LibraryPreferences

// KMK -->

/** A named set of library filters the user can switch back to in one tap */
@Serializable
data class EggikuSavedFilter(
    val name: String,
    val triStates: Map<String, String> = emptyMap(),
    val genresInclude: Set<String> = emptySet(),
    val genresExclude: Set<String> = emptySet(),
)

object EggikuSavedFilters {
    private val json = Json { ignoreUnknownKeys = true }

    private fun LibraryPreferences.triStatePrefs(): Map<String, Preference<TriState>> = mapOf(
        "downloaded" to filterDownloaded(),
        "unread" to filterUnread(),
        "started" to filterStarted(),
        "bookmarked" to filterBookmarked(),
        "completed" to filterCompleted(),
        "lewd" to filterLewd(),
        "ongoing" to filterOngoing(),
        "paused" to filterPausedOrCancelled(),
        "missingSource" to filterMissingSource(),
        "notReadRecently" to filterNotReadRecently(),
        "noNewChapters" to filterNoNewChapters(),
    )

    fun load(prefs: LibraryPreferences): List<EggikuSavedFilter> {
        return prefs.savedFilters().get()
            .mapNotNull { runCatching { json.decodeFromString(EggikuSavedFilter.serializer(), it) }.getOrNull() }
            .sortedBy { it.name.lowercase() }
    }

    fun saveCurrent(prefs: LibraryPreferences, name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val filter = EggikuSavedFilter(
            name = trimmed,
            triStates = prefs.triStatePrefs()
                .mapValues { it.value.get() }
                .filterValues { it != TriState.DISABLED }
                .mapValues { it.value.name },
            genresInclude = prefs.filterGenresInclude().get(),
            genresExclude = prefs.filterGenresExclude().get(),
        )
        val others = load(prefs).filterNot { it.name.equals(trimmed, ignoreCase = true) }
        prefs.savedFilters().set(
            (others + filter).map { json.encodeToString(EggikuSavedFilter.serializer(), it) }.toSet(),
        )
    }

    fun apply(prefs: LibraryPreferences, filter: EggikuSavedFilter) {
        prefs.triStatePrefs().forEach { (key, pref) ->
            val value = filter.triStates[key]?.let { name -> TriState.entries.firstOrNull { it.name == name } }
            pref.set(value ?: TriState.DISABLED)
        }
        prefs.filterGenresInclude().set(filter.genresInclude)
        prefs.filterGenresExclude().set(filter.genresExclude)
    }

    fun delete(prefs: LibraryPreferences, filter: EggikuSavedFilter) {
        prefs.savedFilters().set(
            load(prefs)
                .filterNot { it.name == filter.name }
                .map { json.encodeToString(EggikuSavedFilter.serializer(), it) }
                .toSet(),
        )
    }

    /** Turns every filter off */
    fun clear(prefs: LibraryPreferences) {
        apply(prefs, EggikuSavedFilter(name = ""))
    }
}
// KMK <--
