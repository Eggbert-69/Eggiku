package eu.kanade.tachiyomi.data.rating

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku: your own 1–5 star rating for each series. Kept as one small set of "mangaId:stars" so
 * the Top shelf can read every rating at once; also saved in backups.
 */
object EggikuRatings {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun pref(): Preference<Set<String>> = preferenceStore.getStringSet("eggiku_ratings", emptySet())

    fun parse(entries: Set<String>): Map<Long, Int> = entries.mapNotNull {
        val id = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val stars = it.substringAfter(':').toIntOrNull()?.takeIf { s -> s in 1..5 } ?: return@mapNotNull null
        id to stars
    }.toMap()

    fun all(): Map<Long, Int> = parse(pref().get())

    fun get(mangaId: Long): Int = all()[mangaId] ?: 0

    /** 0 clears the rating */
    fun set(mangaId: Long, stars: Int) {
        val current = all().toMutableMap()
        if (stars in 1..5) current[mangaId] = stars else current.remove(mangaId)
        pref().set(current.map { (id, s) -> "$id:$s" }.toSet())
    }

    /** Moves a rating to a new entry, e.g. after a migration or a duplicate merge */
    fun move(fromId: Long, toId: Long) {
        val stars = get(fromId)
        if (stars == 0 || get(toId) != 0) return
        set(toId, stars)
    }
}
// KMK <--
