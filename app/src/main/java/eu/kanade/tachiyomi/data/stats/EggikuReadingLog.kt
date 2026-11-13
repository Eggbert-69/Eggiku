package eu.kanade.tachiyomi.data.stats

import android.content.res.Resources
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.music.EggikuMusic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

// KMK -->

/**
 * Eggiku: a small day-by-day log of reading (chapters, time, swipes, zooms) that streaks,
 * goals and recaps are built from. It lives in app preferences, so it's included in backups,
 * and it stays on the phone.
 */
object EggikuReadingLog {

    @Serializable
    data class DayStats(
        val chapters: Int = 0,
        val seconds: Long = 0,
        /** Millimetres the page moved under your finger (scrolling and page swipes) */
        val swipeMm: Double = 0.0,
        /** Millimetres moved around zoomed pages */
        val panMm: Double = 0.0,
        val zooms: Int = 0,
        /** Page turns by tapping or volume keys */
        val taps: Int = 0,
        /** Chapters read per series (series in locked categories are left out) */
        val series: Map<Long, Int> = emptyMap(),
        /** Zooms per series (same rule) */
        val seriesZooms: Map<Long, Int> = emptyMap(),
        /** Seconds read per hour of the day */
        val hours: Map<Int, Long> = emptyMap(),
        /** Longest single reading stretch, in seconds */
        val longestSession: Long = 0,
        /** Which side padding was used, by reading time: "folded:15" -> seconds */
        val padding: Map<String, Long> = emptyMap(),
    ) {
        val counts: Boolean get() = chapters > 0 || seconds >= 5 * 60
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val logSerializer = MapSerializer(String.serializer(), DayStats.serializer())
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private fun logPref() = preferenceStore.getString("eggiku_reading_log", "")

    /** Hour the reading day starts, so late-night reading counts for the day before */
    fun dayStartHour() = preferenceStore.getInt("eggiku_stats_day_start_hour", 4)

    /** 0 = no goal */
    fun weeklyGoalChapters() = preferenceStore.getInt("eggiku_stats_weekly_goal", 0)

    fun weeklyRecap() = preferenceStore.getBoolean("eggiku_stats_weekly_recap", true)

    fun nightlyReminder() = preferenceStore.getBoolean("eggiku_stats_nightly_reminder", false)

    private val lock = Any()
    private var days: MutableMap<String, DayStats>? = null
    private var dirty = false

    private val _changes = MutableStateFlow(0)

    /** Bumps whenever the log is saved, so screens can refresh */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun load(): MutableMap<String, DayStats> {
        days?.let { return it }
        val raw = logPref().get()
        val loaded = if (raw.isBlank()) {
            mutableMapOf()
        } else {
            try {
                json.decodeFromString(logSerializer, raw).toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        days = loaded
        return loaded
    }

    fun today(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        now.minusHours(dayStartHour().get().toLong()).toLocalDate()

    private inline fun update(crossinline block: (DayStats) -> DayStats) {
        synchronized(lock) {
            val map = load()
            val key = today().toString()
            map[key] = block(map[key] ?: DayStats())
            dirty = true
        }
    }

    fun chapterRead(mangaId: Long, isPrivate: Boolean) = update { day ->
        day.copy(
            chapters = day.chapters + 1,
            series = if (isPrivate) day.series else day.series + (mangaId to (day.series[mangaId] ?: 0) + 1),
        )
    }

    fun timeRead(seconds: Long, paddingKey: String?) {
        if (EggikuMusic.nowPlaying.value?.isPlaying == true) {
            EggikuAchievements.bump(EggikuAchievements.COUNT_MUSIC_SECONDS, seconds)
        }
        if (seconds <= 0) return
        val hour = LocalDateTime.now().hour
        update { day ->
            day.copy(
                seconds = day.seconds + seconds,
                hours = day.hours + (hour to (day.hours[hour] ?: 0L) + seconds),
                longestSession = maxOf(day.longestSession, seconds),
                padding = if (paddingKey == null) {
                    day.padding
                } else {
                    day.padding + (paddingKey to (day.padding[paddingKey] ?: 0L) + seconds)
                },
            )
        }
    }

    // Movement arrives many times a second, so it's added up here and folded in on flush
    @Volatile private var pendingSwipePx = 0.0

    @Volatile private var pendingPanPx = 0.0

    fun swiped(pixels: Float) {
        pendingSwipePx += abs(pixels)
    }

    fun panned(pixels: Float) {
        pendingPanPx += abs(pixels)
    }

    fun tapped() = update { it.copy(taps = it.taps + 1) }

    /** The series open in the reader, so zooms can be credited to it */
    @Volatile var readerMangaId: Long? = null

    @Volatile var readerMangaPrivate: Boolean = false

    private var lastZoomAt = 0L

    /** One pinch fires many scale changes; count it once */
    fun zoomGesture(now: Long = System.currentTimeMillis()) {
        if (now - lastZoomAt < 800) {
            lastZoomAt = now
            return
        }
        lastZoomAt = now
        zoomed(readerMangaId, readerMangaPrivate)
    }

    fun zoomed(mangaId: Long?, isPrivate: Boolean) = update { day ->
        day.copy(
            zooms = day.zooms + 1,
            seriesZooms = if (mangaId == null || isPrivate) {
                day.seriesZooms
            } else {
                day.seriesZooms + (mangaId to (day.seriesZooms[mangaId] ?: 0) + 1)
            },
        )
    }

    /** Writes everything to storage. Call when leaving the reader. */
    fun flush() {
        val pxPerMm = Resources.getSystem().displayMetrics.ydpi / 25.4
        val swipe = pendingSwipePx
        val pan = pendingPanPx
        pendingSwipePx = 0.0
        pendingPanPx = 0.0
        if (swipe > 0 || pan > 0) {
            update { day ->
                day.copy(swipeMm = day.swipeMm + swipe / pxPerMm, panMm = day.panMm + pan / pxPerMm)
            }
        }
        val snapshot = synchronized(lock) {
            if (!dirty) return
            dirty = false
            load().toMap()
        }
        logPref().set(json.encodeToString(logSerializer, snapshot))
        _changes.value++
        EggikuWidgetBridge.updateStats()
    }

    fun all(): Map<LocalDate, DayStats> = synchronized(lock) {
        load().mapNotNull { (key, value) ->
            try {
                LocalDate.parse(key) to value
            } catch (_: Exception) {
                null
            }
        }.toMap()
    }

    // region Streaks

    data class Streak(
        val current: Int,
        val longest: Int,
        val freezes: Int,
        /** Whether today already counts */
        val readToday: Boolean,
    )

    /**
     * A day counts after a chapter or 5 minutes of reading. Every 7 days in a row earns a freeze
     * (up to 2), and a freeze is used up automatically to cover one missed day.
     */
    fun streak(log: Map<LocalDate, DayStats> = all(), today: LocalDate = today()): Streak {
        val first = log.filterValues { it.counts }.keys.minOrNull()
            ?: return Streak(0, 0, 0, false)
        var current = 0
        var longest = 0
        var freezes = 0
        var day = first
        while (!day.isAfter(today)) {
            val counted = log[day]?.counts == true
            when {
                counted -> {
                    current++
                    if (current % 7 == 0) freezes = (freezes + 1).coerceAtMost(2)
                }
                day == today -> Unit // today isn't over yet
                freezes > 0 -> freezes--
                else -> current = 0
            }
            longest = maxOf(longest, current)
            day = day.plusDays(1)
        }
        return Streak(current, longest, freezes, log[today]?.counts == true)
    }

    // endregion

    // region Ranges

    fun weekStart(today: LocalDate = today()): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun sum(log: Map<LocalDate, DayStats>, from: LocalDate, to: LocalDate): DayStats {
        return log.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values
            .fold(DayStats()) { acc, d ->
                acc.copy(
                    chapters = acc.chapters + d.chapters,
                    seconds = acc.seconds + d.seconds,
                    swipeMm = acc.swipeMm + d.swipeMm,
                    panMm = acc.panMm + d.panMm,
                    zooms = acc.zooms + d.zooms,
                    taps = acc.taps + d.taps,
                    series = (acc.series.keys + d.series.keys).associateWith {
                        (acc.series[it] ?: 0) + (d.series[it] ?: 0)
                    },
                    seriesZooms = (acc.seriesZooms.keys + d.seriesZooms.keys).associateWith {
                        (acc.seriesZooms[it] ?: 0) + (d.seriesZooms[it] ?: 0)
                    },
                    hours = (acc.hours.keys + d.hours.keys).associateWith {
                        (acc.hours[it] ?: 0L) + (d.hours[it] ?: 0L)
                    },
                    longestSession = maxOf(acc.longestSession, d.longestSession),
                    padding = (acc.padding.keys + d.padding.keys).associateWith {
                        (acc.padding[it] ?: 0L) + (d.padding[it] ?: 0L)
                    },
                )
            }
    }

    /** Series read in this range that weren't read before it */
    fun newSeries(log: Map<LocalDate, DayStats>, from: LocalDate, to: LocalDate): Set<Long> {
        val before = log.filterKeys { it.isBefore(from) }.values.flatMap { it.series.keys }.toSet()
        val during = log.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values.flatMap { it.series.keys }.toSet()
        return during - before
    }

    // endregion
}
// KMK <--
