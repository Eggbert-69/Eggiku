package eu.kanade.tachiyomi.data.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

// KMK -->

/** Eggiku: eggs hatched by finishing egg timer reading sessions, kept in a weekly carton */
object EggikuEggCarton {

    data class Egg(val day: LocalDate, val minutes: Int, val at: Long)

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Entries are "epochDay:minutes:millis" */
    private fun pref() = preferenceStore.getStringSet("eggiku_egg_carton_v1", emptySet())

    fun add(minutes: Int) {
        val entry = "${EggikuReadingLog.today().toEpochDay()}:$minutes:${System.currentTimeMillis()}"
        pref().set(pref().get() + entry)
    }

    private fun parse(entries: Set<String>): List<Egg> = entries.mapNotNull { entry ->
        val parts = entry.split(':')
        val day = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        Egg(LocalDate.ofEpochDay(day), minutes, parts.getOrNull(2)?.toLongOrNull() ?: 0L)
    }.sortedBy { it.at }

    fun all(): List<Egg> = parse(pref().get())

    fun changes(): Flow<List<Egg>> = pref().changes().map(::parse)

    /** This week's eggs (the week starts on the same day as the reading stats) */
    fun thisWeek(eggs: List<Egg>, today: LocalDate = EggikuReadingLog.today()): List<Egg> {
        val start = EggikuReadingLog.weekStart(today)
        return eggs.filter { !it.day.isBefore(start) && !it.day.isAfter(today) }
    }
}
// KMK <--
