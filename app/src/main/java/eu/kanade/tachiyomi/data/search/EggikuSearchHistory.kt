package eu.kanade.tachiyomi.data.search

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Eggiku: recent searches in Browse, plus the ones you've saved */
object EggikuSearchHistory {

    private const val SEP = '\u001F'
    private const val MAX_RECENT = 15

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun recentPref() = preferenceStore.getString("eggiku_search_recent", "")

    fun savedPref() = preferenceStore.getString("eggiku_search_saved", "")

    fun parse(raw: String): List<String> = raw.split(SEP).filter { it.isNotBlank() }

    private fun join(list: List<String>) = list.joinToString(SEP.toString())

    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val recent = parse(recentPref().get()).filterNot { it.equals(q, ignoreCase = true) }
        recentPref().set(join((listOf(q) + recent).take(MAX_RECENT)))
    }

    fun toggleSaved(query: String) {
        val saved = parse(savedPref().get())
        savedPref().set(join(if (query in saved) saved - query else listOf(query) + saved))
    }

    fun forget(query: String) {
        recentPref().set(join(parse(recentPref().get()) - query))
    }

    fun clearRecent() = recentPref().set("")
}
// KMK <--
