package eu.kanade.tachiyomi.data.library

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: remembers which sources keep failing during library updates, so a broken source is
 * easy to spot in Browse. A source counts as failing when every entry from it failed in an update,
 * and stops counting as soon as one entry from it updates fine.
 */
object EggikuSourceHealth {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Entries look like "sourceId:failingSinceMillis" */
    fun failing(): Preference<Set<String>> =
        preferenceStore.getStringSet(Preference.appStateKey("eggiku_failing_sources"), emptySet())

    fun parse(entries: Set<String>): Map<Long, Long> = entries.mapNotNull {
        val id = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val since = it.substringAfter(':').toLongOrNull() ?: 0L
        id to since
    }.toMap()

    /** [results] holds one (sourceId, succeeded) pair per entry that was updated */
    fun record(results: List<Pair<Long, Boolean>>, now: Long = System.currentTimeMillis()) {
        if (results.isEmpty()) return
        val current = parse(failing().get()).toMutableMap()
        results.groupBy({ it.first }, { it.second }).forEach { (sourceId, outcomes) ->
            if (outcomes.any { it }) {
                current.remove(sourceId)
            } else if (sourceId !in current) {
                current[sourceId] = now
            }
        }
        failing().set(current.map { (id, since) -> "$id:$since" }.toSet())
    }
}
// KMK <--
