package eu.kanade.tachiyomi.data.shelves

import android.content.Context
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import kotlin.math.ceil

// KMK -->

/**
 * Eggiku shelves: everything about a series that isn't a category. Which series you dropped and
 * why, which ones you're waiting on to finish so you can binge them, rereads, and how series
 * link together (sequels, spin-offs, side stories). All of it lives in one small JSON preference,
 * so it goes into backups with the rest of your settings.
 */
object EggikuShelves {

    /** Why a series was dropped. Kept as the enum name so new reasons don't break old data. */
    enum class Reason { ART, SLOW, STORY, LOST_INTEREST, TOO_LONG, OTHER }

    /** How [Link.to] relates to [Link.from] */
    enum class Kind { SEQUEL, PREQUEL, SPINOFF, SIDE_STORY }

    @Serializable
    data class Drop(
        val at: Long,
        val reason: String = Reason.OTHER.name,
        val note: String = "",
        /** Chapters the series had when dropped, to spot it growing a lot */
        val chaptersAt: Long = 0,
        /** Status when dropped, to spot it finishing */
        val statusAt: Long = 0,
    ) {
        val reasonEnum: Reason get() = Reason.entries.firstOrNull { it.name == reason } ?: Reason.OTHER
    }

    @Serializable
    data class Reread(
        val count: Int = 0,
        val active: Boolean = false,
        val startedAt: Long = 0,
        /** Chapters that were read when the reread started; they're marked read again at the end */
        val snapshot: List<Long> = emptyList(),
    )

    @Serializable
    data class Link(val from: Long, val to: Long, val kind: String) {
        val kindEnum: Kind get() = Kind.entries.firstOrNull { it.name == kind } ?: Kind.SIDE_STORY
    }

    /** A possible spin-off found in a source, waiting for you to look at it */
    @Serializable
    data class Found(
        val parentId: Long,
        val sourceId: Long,
        val url: String,
        val title: String,
        val thumbnail: String? = null,
        val at: Long = 0,
    ) {
        val key: String get() = "$sourceId|$url"
    }

    @Serializable
    data class Store(
        val dropped: Map<Long, Drop> = emptyMap(),
        /** Series id -> when it was added to the list */
        val waiting: Map<Long, Long> = emptyMap(),
        val rereads: Map<Long, Reread> = emptyMap(),
        val links: List<Link> = emptyList(),
        val found: List<Found> = emptyList(),
        /** Spin-offs already shown once (found or dismissed), "sourceId|url" */
        val seen: Set<String> = emptySet(),
        /** Waiting-list series already announced as finished */
        val announced: Set<Long> = emptySet(),
        /** Dropped series already given a second-chance nudge */
        val nudged: Set<Long> = emptySet(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val lock = Any()

    fun pref(): Preference<String> = preferenceStore.getString("eggiku_shelves", "")

    fun parse(raw: String): Store = if (raw.isBlank()) {
        Store()
    } else {
        try {
            json.decodeFromString<Store>(raw)
        } catch (_: Exception) {
            Store()
        }
    }

    fun store(): Store = parse(pref().get())

    fun update(block: (Store) -> Store) = synchronized(lock) {
        pref().set(json.encodeToString(block(store())))
    }

    // ---- Dropped ----

    fun drop(mangaId: Long, reason: Reason, note: String, totalChapters: Long, status: Long) = update { store ->
        val entry = Drop(
            at = System.currentTimeMillis(),
            reason = reason.name,
            note = note.trim(),
            chaptersAt = totalChapters,
            statusAt = status,
        )
        store.copy(
            dropped = store.dropped + (mangaId to entry),
            waiting = store.waiting - mangaId,
            nudged = store.nudged - mangaId,
        )
    }

    fun undrop(mangaId: Long) = update { it.copy(dropped = it.dropped - mangaId, nudged = it.nudged - mangaId) }

    // ---- Waiting to finish ----

    fun setWaiting(mangaId: Long, waiting: Boolean) = update { store ->
        if (waiting) {
            store.copy(waiting = store.waiting + (mangaId to System.currentTimeMillis()), announced = store.announced - mangaId)
        } else {
            store.copy(waiting = store.waiting - mangaId, announced = store.announced - mangaId)
        }
    }

    fun isFinished(status: Long): Boolean =
        status.toInt() == SManga.COMPLETED || status.toInt() == SManga.PUBLISHING_FINISHED

    // ---- Rereads ----

    /** Marks every chapter unread so the series reads from the start, remembering what was read */
    suspend fun startReread(mangaId: Long) = withIOContext {
        val chapters = Injekt.get<GetChaptersByMangaId>().await(mangaId)
        val read = chapters.filter { it.read }.map { it.id }
        if (read.isEmpty()) return@withIOContext
        Injekt.get<UpdateChapter>().awaitAll(
            read.map { ChapterUpdate(id = it, read = false, lastPageRead = 0L) },
        )
        update { store ->
            val old = store.rereads[mangaId] ?: Reread()
            val started = old.copy(active = true, startedAt = System.currentTimeMillis(), snapshot = read)
            store.copy(rereads = store.rereads + (mangaId to started))
        }
    }

    /**
     * Ends a reread: everything you'd read before is marked read again (plus anything you read
     * since), and when [finished] the reread counts.
     */
    suspend fun endReread(mangaId: Long, finished: Boolean) = withIOContext {
        val reread = store().rereads[mangaId] ?: return@withIOContext
        if (!reread.active) return@withIOContext
        val chapters = Injekt.get<GetChaptersByMangaId>().await(mangaId)
        val snapshot = reread.snapshot.toSet()
        val toMark = chapters.filter { it.id in snapshot && !it.read }
        if (toMark.isNotEmpty()) {
            Injekt.get<UpdateChapter>().awaitAll(toMark.map { ChapterUpdate(id = it.id, read = true) })
        }
        val ended = reread.copy(count = reread.count + if (finished) 1 else 0, active = false, snapshot = emptyList())
        update { store -> store.copy(rereads = store.rereads + (mangaId to ended)) }
        if (finished) EggikuAchievements.bump(EggikuAchievements.COUNT_REREADS)
    }

    /** Called after a chapter is read: a reread ends once every chapter from before has been read again */
    suspend fun onChapterRead(mangaId: Long) = withIOContext {
        val reread = store().rereads[mangaId]?.takeIf { it.active } ?: return@withIOContext
        val snapshot = reread.snapshot.toSet()
        val chapters = Injekt.get<GetChaptersByMangaId>().await(mangaId)
        val stillToRead = chapters.count { it.id in snapshot && !it.read }
        if (stillToRead == 0) endReread(mangaId, finished = true)
    }

    // ---- Relations ----

    fun link(from: Long, to: Long, kind: Kind) {
        if (from == to) return
        update { store ->
            val others = store.links.filterNot { (it.from == from && it.to == to) || (it.from == to && it.to == from) }
            store.copy(links = others + Link(from, to, kind.name))
        }
    }

    fun unlink(a: Long, b: Long) = update { store ->
        store.copy(links = store.links.filterNot { (it.from == a && it.to == b) || (it.from == b && it.to == a) })
    }

    /** Every series connected to [mangaId], itself included */
    fun universe(mangaId: Long, links: List<Link> = store().links): Set<Long> {
        val seen = mutableSetOf(mangaId)
        val queue = ArrayDeque(listOf(mangaId))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            links.forEach { link ->
                val other = when (id) {
                    link.from -> link.to
                    link.to -> link.from
                    else -> null
                }
                if (other != null && seen.add(other)) queue.addLast(other)
            }
        }
        return seen
    }

    /**
     * The universe in reading order: prequels first, then the main story and its sequels, with
     * spin-offs and side stories after what they spin off from. [title] breaks ties.
     */
    fun readingOrder(ids: Set<Long>, links: List<Link>, title: (Long) -> String): List<Long> {
        // "a comes before b"
        val before = mutableMapOf<Long, MutableSet<Long>>()
        links.filter { it.from in ids && it.to in ids }.forEach { link ->
            val (first, second) = when (link.kindEnum) {
                Kind.PREQUEL -> link.to to link.from
                else -> link.from to link.to
            }
            before.getOrPut(second) { mutableSetOf() }.add(first)
        }
        val result = mutableListOf<Long>()
        val left = ids.toMutableSet()
        while (left.isNotEmpty()) {
            val ready = left.filter { id -> before[id].orEmpty().none { it in left } }
                .ifEmpty { left.toList() } // A loop; just carry on by title
            val next = ready.minBy { title(it).lowercase() }
            result += next
            left -= next
        }
        return result
    }

    /** What [other] is to [mangaId], if they're linked directly */
    fun kindOf(mangaId: Long, other: Long, links: List<Link> = store().links): Kind? {
        links.firstOrNull { it.from == mangaId && it.to == other }?.let { return it.kindEnum }
        val reverse = links.firstOrNull { it.from == other && it.to == mangaId } ?: return null
        return when (reverse.kindEnum) {
            Kind.SEQUEL -> Kind.PREQUEL
            Kind.PREQUEL -> Kind.SEQUEL
            // The other one is what this spins off from; shown as "Main story"
            Kind.SPINOFF, Kind.SIDE_STORY -> null
        }
    }

    fun dismissFound(key: String) = update { store ->
        store.copy(found = store.found.filterNot { it.key == key }, seen = store.seen + key)
    }

    // ---- Moving and backups ----

    /** Moves shelf data to a new entry, e.g. after a migration or a duplicate merge */
    fun move(fromId: Long, toId: Long) = update { store ->
        fun Long.swap() = if (this == fromId) toId else this
        store.copy(
            dropped = store.dropped[fromId]?.let { (store.dropped - fromId) + (toId to it) } ?: store.dropped,
            waiting = store.waiting[fromId]?.let { (store.waiting - fromId) + (toId to it) } ?: store.waiting,
            rereads = store.rereads[fromId]?.takeIf { toId !in store.rereads }
                ?.let { (store.rereads - fromId) + (toId to it.copy(active = false, snapshot = emptyList())) }
                ?: store.rereads,
            links = store.links.map { it.copy(from = it.from.swap(), to = it.to.swap()) }.filter { it.from != it.to },
        )
    }

    // ---- Binge estimates ----

    /** Your usual time per chapter, from the last few months of reading */
    fun secondsPerChapter(): Long {
        val today = EggikuReadingLog.today()
        val recent = EggikuReadingLog.all().filterKeys { it.isAfter(today.minusDays(120)) }.values
        val chapters = recent.sumOf { it.chapters }
        val seconds = recent.sumOf { it.seconds }
        if (chapters < 5 || seconds <= 0) return DEFAULT_SECONDS_PER_CHAPTER
        return (seconds / chapters).coerceIn(30L, 3_600L)
    }

    /** Your usual reading time per day over the last two weeks, counting days off */
    fun secondsPerDay(): Long {
        val today = EggikuReadingLog.today()
        val log = EggikuReadingLog.all()
        return (0L until 14L).sumOf { log[today.minusDays(it)]?.seconds ?: 0L } / 14L
    }

    fun bingeSeconds(unread: Long, perChapter: Long = secondsPerChapter()): Long = unread.coerceAtLeast(0) * perChapter

    /** The day you'd be done at your usual pace, or null if you haven't read lately */
    fun doneBy(seconds: Long, perDay: Long = secondsPerDay(), today: LocalDate = EggikuReadingLog.today()): LocalDate? {
        if (seconds <= 0 || perDay <= 60) return null
        val days = ceil(seconds.toDouble() / perDay).toLong().coerceAtLeast(1)
        if (days > 365) return null
        return today.plusDays(days - 1)
    }

    /** "45 min", "3 h", "3 h 20 min" */
    fun formatTime(context: Context, seconds: Long): String {
        val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> context.stringResource(KMR.strings.shelf_time_minutes, minutes.toInt())
            hours >= 10 || rest == 0L -> context.stringResource(KMR.strings.shelf_time_hours, (if (rest >= 30) hours + 1 else hours).toInt())
            else -> context.stringResource(KMR.strings.shelf_time_hours_minutes, hours.toInt(), rest.toInt())
        }
    }

    private const val DEFAULT_SECONDS_PER_CHAPTER = 5L * 60
}
// KMK <--
