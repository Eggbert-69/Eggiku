package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

// KMK -->

/** Eggiku: what goes in Continue reading besides the series you're reading right now */
object EggikuContinueReading {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private const val SKIP_DAYS = 7L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Entries look like "mangaId:hiddenUntilMillis" */
    fun skipped() = preferenceStore.getStringSet("eggiku_continue_skipped", emptySet())

    private fun parse(entries: Set<String>): Map<Long, Long> = entries.mapNotNull {
        val id = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        id to (it.substringAfter(':').toLongOrNull() ?: 0L)
    }.toMap()

    fun skippedIds(now: Long = System.currentTimeMillis()): Set<Long> =
        parse(skipped().get()).filterValues { it > now }.keys

    /** Swiping a card away hides it for a week */
    fun skip(mangaId: Long, now: Long = System.currentTimeMillis()) {
        val map = parse(skipped().get()).filterValues { it > now }.toMutableMap()
        map[mangaId] = now + SKIP_DAYS * DAY_MS
        skipped().set(map.map { (id, until) -> "$id:$until" }.toSet())
    }

    /**
     * Series you binged and then dropped: 5+ chapters within a week sometime in the last
     * 3 months, but nothing read from them in the last 2 weeks.
     */
    fun droppedBinges(today: LocalDate = EggikuReadingLog.today()): Set<Long> {
        val log = EggikuReadingLog.all()
        val since = today.minusDays(90)
        val recent = today.minusDays(14)
        val days = log.filterKeys { !it.isBefore(since) }.toSortedMap()
        val readRecently = days.filterKeys { !it.isBefore(recent) }.values.flatMap { it.series.keys }.toSet()
        val binged = mutableSetOf<Long>()
        days.keys.forEach { start ->
            val week = days.filterKeys { !it.isBefore(start) && it.isBefore(start.plusDays(7)) }.values
            week.flatMap { it.series.entries }
                .groupBy({ it.key }, { it.value })
                .filterValues { it.sum() >= 5 }
                .keys
                .let(binged::addAll)
        }
        return binged - readRecently
    }
}
// KMK <--
