package eu.kanade.tachiyomi.data.changelog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku patch notes. Every build carries the whole history (assets/eggiku_changelog.json), so after
 * an update the app can show everything since the last build you opened, even if you skipped some.
 */
object EggikuChangelog {

    @Serializable
    data class Entry(val build: Int, val date: String = "", val changes: List<String> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Highest build whose notes you've seen; -1 before this feature existed */
    private fun seen() = preferenceStore.getInt(Preference.appStateKey("eggiku_changelog_seen"), -1)

    fun load(context: Context): List<Entry> = try {
        context.assets.open("eggiku_changelog.json").bufferedReader().use {
            json.decodeFromString<List<Entry>>(it.readText())
        }.sortedByDescending { it.build }
    } catch (_: Exception) {
        emptyList()
    }

    /** Notes to show now: everything newer than what you've seen (just the newest the first time) */
    fun unseen(context: Context): List<Entry> {
        val all = load(context)
        val last = seen().get()
        return if (last < 0) all.take(1) else all.filter { it.build > last }
    }

    fun markSeen(context: Context) {
        load(context).maxOfOrNull { it.build }?.let { seen().set(it) }
    }
}
// KMK <--
