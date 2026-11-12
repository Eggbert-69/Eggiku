package eu.kanade.tachiyomi.data.achievements

import android.content.Context
import androidx.core.content.ContextCompat
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.ui.main.EggikuOpen
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

// KMK -->
/**
 * Eggiku achievements. Each one is worked out from your reading log, ratings and library, so
 * nothing extra is tracked; the date each was first earned is remembered for the badge screen.
 */
object EggikuAchievements {

    data class Achievement(
        val key: String,
        val emoji: String,
        val title: StringResource,
        val description: StringResource,
        /** Progress so far and the target */
        val progress: (Facts) -> Pair<Long, Long>,
    )

    /** Everything the achievements look at, gathered once */
    data class Facts(
        val totalChapters: Long,
        val longestStreak: Long,
        val mostSeriesInADay: Long,
        val lateNightSeconds: Long,
        val earlySeconds: Long,
        val longestSessionSeconds: Long,
        val swipeMm: Double,
        val zooms: Long,
        val rated: Long,
        val fiveStars: Long,
        val librarySize: Long,
        val migrated: Long,
        val weeksGoalMet: Long,
    )

    val all = listOf(
        Achievement("cracked", "🥚", KMR.strings.ach_cracked, KMR.strings.ach_cracked_desc) { it.totalChapters to 1 },
        Achievement("over_easy", "🍳", KMR.strings.ach_over_easy, KMR.strings.ach_over_easy_desc) { it.longestStreak to 3 },
        Achievement("hard_boiled", "💪", KMR.strings.ach_hard_boiled, KMR.strings.ach_hard_boiled_desc) { it.longestStreak to 7 },
        Achievement("soft_serve", "🔥", KMR.strings.ach_month_streak, KMR.strings.ach_month_streak_desc) { it.longestStreak to 30 },
        Achievement("century_egg", "🏺", KMR.strings.ach_century_egg, KMR.strings.ach_century_egg_desc) { it.longestStreak to 100 },
        Achievement("scrambled", "🍳", KMR.strings.ach_scrambled, KMR.strings.ach_scrambled_desc) { it.mostSeriesInADay to 5 },
        Achievement("omelette", "🥞", KMR.strings.ach_omelette, KMR.strings.ach_omelette_desc) { it.totalChapters to 100 },
        Achievement("benedict", "🍽️", KMR.strings.ach_benedict, KMR.strings.ach_benedict_desc) { it.totalChapters to 1000 },
        Achievement("golden_egg", "🌟", KMR.strings.ach_golden_egg, KMR.strings.ach_golden_egg_desc) { it.totalChapters to 10000 },
        Achievement("night_owl", "🦉", KMR.strings.ach_night_owl, KMR.strings.ach_night_owl_desc) { it.lateNightSeconds to 3600 },
        Achievement("early_bird", "🐣", KMR.strings.ach_early_bird, KMR.strings.ach_early_bird_desc) { it.earlySeconds to 1800 },
        Achievement("marathon", "🏃", KMR.strings.ach_marathon, KMR.strings.ach_marathon_desc) { it.longestSessionSeconds to 2 * 3600 },
        Achievement("thumb", "👍", KMR.strings.ach_thumb, KMR.strings.ach_thumb_desc) { it.swipeMm.toLong() to 1_000_000 },
        Achievement("magnifier", "🔍", KMR.strings.ach_magnifier, KMR.strings.ach_magnifier_desc) { it.zooms to 500 },
        Achievement("critic", "⭐", KMR.strings.ach_critic, KMR.strings.ach_critic_desc) { it.rated to 10 },
        Achievement("top_shelf", "🏆", KMR.strings.ach_top_shelf, KMR.strings.ach_top_shelf_desc) { it.fiveStars to 5 },
        Achievement("nest", "🪺", KMR.strings.ach_nest, KMR.strings.ach_nest_desc) { it.librarySize to 100 },
        Achievement("new_nest", "🚚", KMR.strings.ach_new_nest, KMR.strings.ach_new_nest_desc) { it.migrated to 1 },
        Achievement("goal", "🎯", KMR.strings.ach_goal, KMR.strings.ach_goal_desc) { it.weeksGoalMet to 4 },
    )

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** "key:epochDay" for each achievement already earned */
    fun earnedPref() = preferenceStore.getStringSet("eggiku_achievements", emptySet())

    private fun migratedPref() = preferenceStore.getInt("eggiku_ach_migrated", 0)

    fun earned(): Map<String, LocalDate> = earnedPref().get().mapNotNull {
        val key = it.substringBefore(':')
        val day = it.substringAfter(':').toLongOrNull() ?: return@mapNotNull null
        key to LocalDate.ofEpochDay(day)
    }.toMap()

    fun countMigration() = migratedPref().set(migratedPref().get() + 1)

    suspend fun facts(): Facts {
        val log = EggikuReadingLog.all()
        val days = log.values
        val streak = EggikuReadingLog.streak(log)
        val ratings = EggikuRatings.all()
        val goal = EggikuReadingLog.weeklyGoalChapters().get()
        val weeksGoalMet = if (goal <= 0) {
            0L
        } else {
            log.entries.groupBy { EggikuReadingLog.weekStart(it.key) }
                .count { (_, week) -> week.sumOf { it.value.chapters } >= goal }
                .toLong()
        }
        val librarySize = try {
            Injekt.get<GetLibraryManga>().await().distinctBy { it.id }.size.toLong()
        } catch (_: Exception) {
            0L
        }
        return Facts(
            totalChapters = days.sumOf { it.chapters }.toLong(),
            longestStreak = streak.longest.toLong(),
            mostSeriesInADay = days.maxOfOrNull { it.series.size }?.toLong() ?: 0L,
            lateNightSeconds = days.sumOf { day -> (0..3).sumOf { day.hours[it] ?: 0L } },
            earlySeconds = days.sumOf { day -> (5..6).sumOf { day.hours[it] ?: 0L } },
            longestSessionSeconds = days.maxOfOrNull { it.longestSession } ?: 0L,
            swipeMm = days.sumOf { it.swipeMm },
            zooms = days.sumOf { it.zooms }.toLong(),
            rated = ratings.size.toLong(),
            fiveStars = ratings.count { it.value == 5 }.toLong(),
            librarySize = librarySize,
            migrated = migratedPref().get().toLong(),
            weeksGoalMet = weeksGoalMet,
        )
    }

    /** Works out newly earned achievements, remembers them, and returns them */
    suspend fun checkNew(): List<Achievement> {
        val facts = facts()
        val earned = earned()
        val newOnes = all.filter { it.key !in earned }.filter {
            val (have, need) = it.progress(facts)
            have >= need
        }
        if (newOnes.isNotEmpty()) {
            val today = LocalDate.now().toEpochDay()
            earnedPref().set(earnedPref().get() + newOnes.map { "${it.key}:$today" })
        }
        return newOnes
    }

    /** Checks and sends one notification for anything newly earned */
    suspend fun checkAndNotify(context: Context) {
        val newOnes = checkNew()
        val first = newOnes.firstOrNull() ?: return
        context.notify(ID, Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setContentTitle(
                context.stringResource(KMR.strings.ach_unlocked, "${first.emoji} ${context.stringResource(first.title)}"),
            )
            setContentText(
                if (newOnes.size > 1) {
                    context.stringResource(KMR.strings.ach_unlocked_more, newOnes.size - 1)
                } else {
                    context.stringResource(first.description)
                },
            )
            setContentIntent(EggikuOpen.pendingIntent(context, ID, EggikuOpen.ACHIEVEMENTS))
            setAutoCancel(true)
        }
    }

    private const val ID = -1912
}
// KMK <--
