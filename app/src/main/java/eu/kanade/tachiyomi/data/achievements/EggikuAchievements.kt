package eu.kanade.tachiyomi.data.achievements

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.HiddenAppThemes
import eu.kanade.presentation.more.settings.screen.EggikuHiddenThemes
import eu.kanade.presentation.more.stats.EggikuStatsFormat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.main.EggikuOpen
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetReadMangaNotInLibraryView
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

// KMK -->
/**
 * Eggiku achievements.
 *
 * Ladders: one per stat (chapters read, time read, ...), each with lots of small steps so the next
 * one is always within reach. They count everything you've ever read in the app, including series
 * no longer in your library, using the reading history.
 *
 * One-offs: single badges for doing something specific. Secret ones show as ??? until earned.
 */
object EggikuAchievements {

    // Counters for things the history can't tell us
    const val COUNT_QUICK_SORTED = "quick_sorted"
    const val COUNT_COOKED = "cooked"
    const val COUNT_UPDATES = "updates_installed"
    const val COUNT_MERGES = "merges"
    const val COUNT_MIGRATED = "migrated"
    const val COUNT_REVIVED = "revived"
    const val COUNT_MUSIC_SECONDS = "music_seconds"

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private fun counter(key: String) = preferenceStore.getLong("eggiku_ach_count_$key", 0L)

    fun bump(key: String, by: Long = 1L) {
        val pref = counter(key)
        pref.set(pref.get() + by)
    }

    /** Everything the achievements look at, gathered once */
    data class Facts(
        val chaptersRead: Long,
        val hoursRead: Long,
        val finished: Long,
        val started: Long,
        val librarySize: Long,
        val longestStreak: Long,
        val mostChaptersInADay: Long,
        val swipeMm: Long,
        val zooms: Long,
        val rated: Long,
        val anyOneStar: Boolean,
        val nightShiftDays: Long,
        val bestWeekendSeconds: Long,
        val longestSessionSeconds: Long,
        val musicSeconds: Long,
        val sources: Long,
        val languages: Long,
        val unfoldedSeconds: Long,
        val speedrun: Boolean,
        val longHaul: Boolean,
        val backForSeconds: Boolean,
        val weeksGoalMet: Long,
        val counters: Map<String, Long>,
        val glibGlorb: Boolean,
        val hiddenThemes: Set<String>,
    )

    // ---- Ladders ----

    class Ladder(
        val key: String,
        val emoji: String,
        val title: StringResource,
        /** "%1$s" is the formatted target */
        val description: StringResource,
        val thresholds: List<Long>,
        val format: (Long) -> String = ::number,
        val value: (Facts) -> Long,
    ) {
        /** How many tiers are earned */
        fun tiersEarned(facts: Facts): Int = thresholds.count { value(facts) >= it }

        fun tierName(index: Int): StringResource {
            val scaled = if (thresholds.size <= 1) 0 else (index * (TIER_NAMES.size - 1).toFloat() / (thresholds.size - 1)).roundToInt()
            return TIER_NAMES[scaled.coerceIn(0, TIER_NAMES.lastIndex)]
        }

        fun metal(index: Int): Metal {
            if (index >= thresholds.lastIndex) return Metal.Cosmic
            val fraction = index.toFloat() / thresholds.lastIndex
            return when {
                fraction < 0.25f -> Metal.Bronze
                fraction < 0.5f -> Metal.Silver
                fraction < 0.75f -> Metal.Gold
                else -> Metal.Platinum
            }
        }

        fun tierKey(index: Int) = "L:$key:$index"
    }

    enum class Metal(val color: Color) {
        Bronze(Color(0xFFCD7F32)),
        Silver(Color(0xFFC0C6CC)),
        Gold(Color(0xFFFFC53D)),
        Platinum(Color(0xFF9AD8E0)),
        Cosmic(Color(0xFFB388FF)),
    }

    private fun number(value: Long) = String.format(Locale.getDefault(), "%,d", value)

    private const val MM_PER_FOOT = 304.8
    private const val MM_PER_MILE = 1_609_344.0
    private fun feet(value: Double) = (value * MM_PER_FOOT).toLong()
    private fun miles(value: Double) = (value * MM_PER_MILE).toLong()

    val ladders = listOf(
        Ladder(
            "chapters",
            "📖",
            KMR.strings.ach_l_chapters,
            KMR.strings.ach_l_chapters_desc,
            listOf(
                1, 1_000, 10_000, 50_000, 60_000, 70_000, 80_000, 90_000, 100_000, 125_000, 150_000, 175_000,
                200_000, 250_000, 300_000, 400_000, 500_000, 750_000, 1_000_000,
            ).map(Int::toLong),
        ) { it.chaptersRead },
        Ladder(
            "hours",
            "⏳",
            KMR.strings.ach_l_hours,
            KMR.strings.ach_l_hours_desc,
            listOf(
                1, 100, 500, 600, 700, 800, 900, 1_000, 1_250, 1_500, 1_750, 2_000, 2_500, 3_000, 4_000, 5_000,
                7_500, 10_000,
            ).map(Int::toLong),
        ) { it.hoursRead },
        Ladder(
            "finished",
            "🏁",
            KMR.strings.ach_l_finished,
            KMR.strings.ach_l_finished_desc,
            listOf(1, 50, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1_000, 1_250, 1_500, 2_000).map(Int::toLong),
        ) { it.finished },
        Ladder(
            "started",
            "🐣",
            KMR.strings.ach_l_started,
            KMR.strings.ach_l_started_desc,
            listOf(10, 500, 1_500, 1_750, 2_000, 2_250, 2_500, 3_000, 3_500, 4_000, 5_000, 6_000, 7_500, 10_000)
                .map(Int::toLong),
        ) { it.started },
        Ladder(
            "library",
            "🪺",
            KMR.strings.ach_l_library,
            KMR.strings.ach_l_library_desc,
            listOf(10, 250, 500, 550, 600, 650, 700, 800, 900, 1_000, 1_250, 1_500, 2_000, 2_500, 3_000)
                .map(Int::toLong),
        ) { it.librarySize },
        Ladder(
            "streak",
            "🔥",
            KMR.strings.ach_l_streak,
            KMR.strings.ach_l_streak_desc,
            listOf(3, 7, 14, 21, 30, 45, 60, 90, 120, 150, 180, 240, 300, 365, 500, 730, 1_000).map(Int::toLong),
        ) { it.longestStreak },
        Ladder(
            "day",
            "🍳",
            KMR.strings.ach_l_day,
            KMR.strings.ach_l_day_desc,
            listOf(25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1_000).map(Int::toLong),
        ) { it.mostChaptersInADay },
        Ladder(
            "distance",
            "👍",
            KMR.strings.ach_l_distance,
            KMR.strings.ach_l_distance_desc,
            listOf(
                feet(500.0), feet(1_000.0), miles(0.25), miles(0.5), miles(1.0), miles(2.0), miles(3.1), miles(5.0),
                miles(6.2), miles(10.0), miles(13.1), miles(20.0), miles(26.2), miles(50.0), miles(100.0),
            ),
            format = { EggikuStatsFormat.distance(it.toDouble()) },
        ) { it.swipeMm },
        Ladder(
            "zooms",
            "🔍",
            KMR.strings.ach_l_zooms,
            KMR.strings.ach_l_zooms_desc,
            listOf(25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000).map(Int::toLong),
        ) { it.zooms },
        Ladder(
            "ratings",
            "⭐",
            KMR.strings.ach_l_ratings,
            KMR.strings.ach_l_ratings_desc,
            listOf(1, 5, 10, 25, 50, 100, 250, 500).map(Int::toLong),
        ) { it.rated },
    )

    /** Shared egg-themed names for tiers, spread over each ladder so the top is always Cosmic Egg */
    val TIER_NAMES = listOf(
        KMR.strings.ach_tier_1, KMR.strings.ach_tier_2, KMR.strings.ach_tier_3, KMR.strings.ach_tier_4,
        KMR.strings.ach_tier_5, KMR.strings.ach_tier_6, KMR.strings.ach_tier_7, KMR.strings.ach_tier_8,
        KMR.strings.ach_tier_9, KMR.strings.ach_tier_10, KMR.strings.ach_tier_11, KMR.strings.ach_tier_12,
        KMR.strings.ach_tier_13, KMR.strings.ach_tier_14, KMR.strings.ach_tier_15, KMR.strings.ach_tier_16,
        KMR.strings.ach_tier_17, KMR.strings.ach_tier_18, KMR.strings.ach_tier_19, KMR.strings.ach_tier_20,
    )

    // ---- One-offs ----

    class OneOff(
        val key: String,
        val emoji: String,
        val title: StringResource,
        val description: StringResource,
        val secret: Boolean = false,
        val done: (Facts) -> Boolean,
    ) {
        val earnedKey get() = "O:$key"
    }

    private const val HOUR = 3_600L

    val oneOffs = listOf(
        OneOff("speedrun", "⚡", KMR.strings.ach_speedrun, KMR.strings.ach_speedrun_desc) { it.speedrun },
        OneOff("long_haul", "🐢", KMR.strings.ach_long_haul, KMR.strings.ach_long_haul_desc) { it.longHaul },
        OneOff("necromancer", "🧟", KMR.strings.ach_necromancer, KMR.strings.ach_necromancer_desc) {
            (it.counters[COUNT_REVIVED] ?: 0) >= 1
        },
        OneOff("back_for_seconds", "🍽️", KMR.strings.ach_back_for_seconds, KMR.strings.ach_back_for_seconds_desc) {
            it.backForSeconds
        },
        OneOff("night_shift", "🦉", KMR.strings.ach_night_shift, KMR.strings.ach_night_shift_desc) { it.nightShiftDays >= 7 },
        OneOff("weekend", "🛋️", KMR.strings.ach_weekend, KMR.strings.ach_weekend_desc) {
            it.bestWeekendSeconds >= 10 * HOUR
        },
        OneOff("marathon", "🏃", KMR.strings.ach_marathon, KMR.strings.ach_marathon_desc) {
            it.longestSessionSeconds >= 2 * HOUR
        },
        OneOff("iron_seat", "🪑", KMR.strings.ach_iron_seat, KMR.strings.ach_iron_seat_desc) {
            it.longestSessionSeconds >= 5 * HOUR
        },
        OneOff("dj", "🎧", KMR.strings.ach_dj, KMR.strings.ach_dj_desc) { it.musicSeconds >= 10 * HOUR },
        OneOff("globetrotter", "🌍", KMR.strings.ach_globetrotter, KMR.strings.ach_globetrotter_desc) { it.sources >= 10 },
        OneOff("polyglot", "🗣️", KMR.strings.ach_polyglot, KMR.strings.ach_polyglot_desc) { it.languages >= 3 },
        OneOff("unfolded", "📱", KMR.strings.ach_unfolded, KMR.strings.ach_unfolded_desc) {
            it.unfoldedSeconds >= 100 * HOUR
        },
        OneOff("goal", "🎯", KMR.strings.ach_goal, KMR.strings.ach_goal_desc) { it.weeksGoalMet >= 4 },
        OneOff("new_nest", "🚚", KMR.strings.ach_new_nest, KMR.strings.ach_new_nest_desc) {
            (it.counters[COUNT_MIGRATED] ?: 0) >= 1
        },
        OneOff("clean_coop", "🧹", KMR.strings.ach_clean_coop, KMR.strings.ach_clean_coop_desc) {
            (it.counters[COUNT_MERGES] ?: 0) >= 1
        },
        OneOff("sorting_hat", "🎩", KMR.strings.ach_sorting_hat, KMR.strings.ach_sorting_hat_desc) {
            (it.counters[COUNT_QUICK_SORTED] ?: 0) >= 50
        },
        OneOff("harsh_critic", "🥀", KMR.strings.ach_harsh_critic, KMR.strings.ach_harsh_critic_desc) { it.anyOneStar },
        OneOff("patience", "⏲️", KMR.strings.ach_patience, KMR.strings.ach_patience_desc) {
            (it.counters[COUNT_COOKED] ?: 0) >= 1
        },
        // Secrets
        OneOff("liftoff", "🚀", KMR.strings.ach_liftoff, KMR.strings.ach_liftoff_desc, secret = true) { it.glibGlorb },
        OneOff("sunny", "🌞", KMR.strings.ach_sunny, KMR.strings.ach_sunny_desc, secret = true) {
            AppTheme.SUNNY_SIDE_UP.name in it.hiddenThemes
        },
        OneOff("arcade", "🕹️", KMR.strings.ach_arcade, KMR.strings.ach_arcade_desc, secret = true) {
            AppTheme.MIDNIGHT_ARCADE.name in it.hiddenThemes
        },
        OneOff("egg_hunter", "🧺", KMR.strings.ach_egg_hunter, KMR.strings.ach_egg_hunter_desc, secret = true) { facts ->
            facts.glibGlorb && HiddenAppThemes.filter { it != AppTheme.GLIB_GLORB }.all { it.name in facts.hiddenThemes }
        },
        OneOff("up_to_date", "🆙", KMR.strings.ach_up_to_date, KMR.strings.ach_up_to_date_desc, secret = true) {
            (it.counters[COUNT_UPDATES] ?: 0) >= 1
        },
    )

    val totalCount: Int get() = ladders.sumOf { it.thresholds.size } + oneOffs.size

    // ---- Earned ----

    /** "key:epochDay" for every tier and badge earned */
    fun earnedPref() = preferenceStore.getStringSet("eggiku_achievements_v2", emptySet())

    private fun seeded() = preferenceStore.getBoolean("eggiku_achievements_v2_seeded", false)

    fun earned(): Map<String, LocalDate> = earnedPref().get().mapNotNull {
        val key = it.substringBeforeLast(':')
        val day = it.substringAfterLast(':').toLongOrNull() ?: return@mapNotNull null
        key to LocalDate.ofEpochDay(day)
    }.toMap()

    // ---- Working out the facts ----

    private data class ReadEvent(val mangaId: Long, val at: Long, val ms: Long)

    suspend fun facts(): Facts {
        val zone = ZoneId.systemDefault()
        val dayStart = EggikuReadingLog.dayStartHour().get().toLong()
        fun readingDay(millis: Long): LocalDate =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).minusHours(dayStart).toLocalDate()

        val library = Injekt.get<GetLibraryManga>().await().distinctBy { it.id }
        val everything = (library + Injekt.get<GetReadMangaNotInLibraryView>().await()).distinctBy { it.id }
        val events = Injekt.get<DatabaseHandler>().awaitList {
            historyQueries.eggikuReadEvents { mangaId, lastRead, timeRead ->
                ReadEvent(mangaId, lastRead?.time ?: 0L, timeRead)
            }
        }.filter { it.at > 0 }

        val log = EggikuReadingLog.all()

        // Per reading day: chapters and seconds, from history and from Eggiku's own log (whichever is more)
        val dayChapters = HashMap<LocalDate, Long>()
        val daySeconds = HashMap<LocalDate, Long>()
        events.forEach { e ->
            val d = readingDay(e.at)
            dayChapters[d] = (dayChapters[d] ?: 0) + 1
            daySeconds[d] = (daySeconds[d] ?: 0) + e.ms / 1000
        }
        log.forEach { (d, stats) ->
            if (stats.chapters > (dayChapters[d] ?: 0)) dayChapters[d] = stats.chapters.toLong()
            if (stats.seconds > (daySeconds[d] ?: 0)) daySeconds[d] = stats.seconds
        }
        val readDays = (dayChapters.filterValues { it > 0 }.keys + log.filterValues { it.counts }.keys).sorted()
        var longest = 0L
        var run = 0L
        var previous: LocalDate? = null
        readDays.forEach { d ->
            run = if (previous != null && previous!!.plusDays(1) == d) run + 1 else 1
            longest = maxOf(longest, run)
            previous = d
        }

        val nightDays = events.mapNotNull { e ->
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at), zone)
            time.toLocalDate().takeIf { time.hour in 3..4 }
        }.toSet() + log.filterValues { (it.hours[3] ?: 0) + (it.hours[4] ?: 0) > 0 }.keys

        val bestWeekend = daySeconds.keys
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY }
            .maxOfOrNull { (daySeconds[it] ?: 0) + (daySeconds[it.plusDays(1)] ?: 0) } ?: 0L

        // Per series: when its chapters were read
        val byManga = events.groupBy { it.mangaId }.mapValues { (_, list) -> list.map { it.at }.sorted() }
        val finishedSeries = everything.filter { it.totalChapters > 0 && it.unreadCount == 0L }
        val dayMs = 24L * 60 * 60 * 1000
        val speedrun = finishedSeries.any { m ->
            val times = byManga[m.id] ?: return@any false
            m.totalChapters >= 100 && times.size >= 90 && times.last() - times.first() <= 7 * dayMs
        }
        val longHaul = finishedSeries.any { m ->
            val times = byManga[m.id] ?: return@any false
            m.totalChapters >= 10 && times.last() - times.first() >= 365 * dayMs
        }
        val backForSeconds = byManga.values.any { times -> times.zipWithNext().any { (a, b) -> b - a >= 180 * dayMs } }

        val sourceManager = Injekt.get<SourceManager>()
        val sources = everything.filter { it.hasStarted }.map { it.manga.source }.toSet()
        val languages = sources.mapNotNull { id ->
            sourceManager.getOrStub(id).lang.takeIf { it.isNotBlank() && it != "all" && it != "other" }
        }.toSet()

        val ratings = EggikuRatings.all()
        val goal = EggikuReadingLog.weeklyGoalChapters().get()
        val weeksGoalMet = if (goal <= 0) {
            0L
        } else {
            log.entries.groupBy { EggikuReadingLog.weekStart(it.key) }
                .count { (_, week) -> week.sumOf { it.value.chapters } >= goal }
                .toLong()
        }
        val ui = Injekt.get<UiPreferences>()

        return Facts(
            chaptersRead = everything.sumOf { it.readCount },
            hoursRead = Injekt.get<GetTotalReadDuration>().await() / 3_600_000L,
            finished = everything.count { it.manga.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L }.toLong(),
            started = everything.count { it.hasStarted }.toLong(),
            librarySize = library.size.toLong(),
            longestStreak = longest,
            mostChaptersInADay = dayChapters.values.maxOrNull() ?: 0L,
            swipeMm = log.values.sumOf { it.swipeMm }.toLong(),
            zooms = log.values.sumOf { it.zooms }.toLong(),
            rated = ratings.size.toLong(),
            anyOneStar = ratings.values.any { it == 1 },
            nightShiftDays = nightDays.size.toLong(),
            bestWeekendSeconds = bestWeekend,
            longestSessionSeconds = log.values.maxOfOrNull { it.longestSession } ?: 0L,
            musicSeconds = counter(COUNT_MUSIC_SECONDS).get(),
            sources = sources.size.toLong(),
            languages = languages.size.toLong(),
            unfoldedSeconds = log.values.sumOf { day ->
                day.padding.filterKeys { it.startsWith("unfolded") }.values.sum()
            },
            speedrun = speedrun,
            longHaul = longHaul,
            backForSeconds = backForSeconds,
            weeksGoalMet = weeksGoalMet,
            counters = listOf(
                COUNT_QUICK_SORTED,
                COUNT_COOKED,
                COUNT_UPDATES,
                COUNT_MERGES,
                COUNT_MIGRATED,
                COUNT_REVIVED,
            ).associateWith { counter(it).get() },
            glibGlorb = ui.glibGlorbUnlocked().get(),
            hiddenThemes = ui.unlockedHiddenThemes().get(),
        )
    }

    data class NewlyEarned(val emoji: String, val title: StringResource, val tierName: StringResource?)

    /** Works out newly earned tiers and badges, remembers them, and returns them */
    suspend fun checkNew(facts: Facts? = null): List<NewlyEarned> {
        val f = facts ?: facts()
        val earned = earned()
        val today = LocalDate.now().toEpochDay()
        val newKeys = mutableListOf<String>()
        val newOnes = mutableListOf<NewlyEarned>()
        ladders.forEach { ladder ->
            repeat(ladder.tiersEarned(f)) { index ->
                val key = ladder.tierKey(index)
                if (key !in earned) {
                    newKeys += key
                    newOnes += NewlyEarned(ladder.emoji, ladder.title, ladder.tierName(index))
                }
            }
        }
        oneOffs.filter { it.earnedKey !in earned && it.done(f) }.forEach {
            newKeys += it.earnedKey
            newOnes += NewlyEarned(it.emoji, it.title, null)
        }
        if (newKeys.isNotEmpty()) earnedPref().set(earnedPref().get() + newKeys.map { "$it:$today" })
        return newOnes
    }

    /** Checks and sends one notification for anything newly earned */
    suspend fun checkAndNotify(context: Context) {
        val facts = facts()
        val newOnes = checkNew(facts)
        // Hidden themes earned by reading
        val themes = buildList {
            if (facts.longestStreak >= 7 && EggikuHiddenThemes.grant(AppTheme.HOT_SAUCE)) add(AppTheme.HOT_SAUCE)
            if (earned().size >= 50 && EggikuHiddenThemes.grant(AppTheme.GOLDEN_HOUR)) add(AppTheme.GOLDEN_HOUR)
        }
        themes.forEach { theme ->
            context.notify(ID_THEME + theme.ordinal, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(
                    context.stringResource(
                        KMR.strings.hidden_theme_earned,
                        context.stringResource(theme.titleRes ?: KMR.strings.theme_custom),
                    ),
                )
                setContentText(context.stringResource(KMR.strings.hidden_theme_earned_text))
                setAutoCancel(true)
            }
        }
        // The first check after installing counts all past reading at once: no flood of notifications
        if (!seeded().get()) {
            seeded().set(true)
            return
        }
        // Several tiers of the same ladder at once: only mention the highest
        val shown = newOnes.reversed().distinctBy { it.title }.reversed()
        val first = shown.lastOrNull() ?: return
        context.notify(ID, Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            val name = first.tierName?.let { "${context.stringResource(first.title)}: ${context.stringResource(it)}" }
                ?: context.stringResource(first.title)
            setContentTitle(context.stringResource(KMR.strings.ach_unlocked, "${first.emoji} $name"))
            setContentText(
                if (shown.size > 1) {
                    context.stringResource(KMR.strings.ach_unlocked_more, shown.size - 1)
                } else {
                    context.stringResource(KMR.strings.ach_unlocked_tap)
                },
            )
            setContentIntent(EggikuOpen.pendingIntent(context, ID, EggikuOpen.ACHIEVEMENTS))
            setAutoCancel(true)
        }
    }

    private const val ID = -1912
    private const val ID_THEME = -1950
}
// KMK <--
