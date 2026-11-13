package eu.kanade.tachiyomi.data.cook

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDateTime
import java.time.ZoneId

// KMK -->

/**
 * Eggiku cook timer: "let this series cook" for a few weeks or months while chapters pile up.
 * When the timer is done you get a notification and the series is put in Continue reading
 * until you open it in the reader.
 */
object EggikuCookTimer {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Entries look like "mangaId:dueMillis" */
    fun timers() = preferenceStore.getStringSet("eggiku_cook_timers", emptySet())

    /** Series whose timer finished, shown in Continue reading until they're read */
    fun ready() = preferenceStore.getStringSet("eggiku_cook_ready", emptySet())

    fun parse(entries: Set<String>): Map<Long, Long> = entries.mapNotNull {
        val id = it.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val due = it.substringAfter(':').toLongOrNull() ?: return@mapNotNull null
        id to due
    }.toMap()

    fun dueAt(mangaId: Long): Long? = parse(timers().get())[mangaId]

    /** "Cook until X new chapters". Entries look like "mangaId:goal:chapterCountWhenStarted" */
    fun chapterGoalsPref() = preferenceStore.getStringSet("eggiku_cook_chapter_goals", emptySet())

    /** mangaId -> (new chapters wanted, chapter count when started) */
    fun chapterGoals(entries: Set<String> = chapterGoalsPref().get()): Map<Long, Pair<Int, Int>> =
        entries.mapNotNull {
            val parts = it.split(':')
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val goal = parts[1].toIntOrNull() ?: return@mapNotNull null
            val base = parts[2].toIntOrNull() ?: return@mapNotNull null
            id to (goal to base)
        }.toMap()

    private fun saveGoals(map: Map<Long, Pair<Int, Int>>) {
        chapterGoalsPref().set(map.map { (id, v) -> "$id:${v.first}:${v.second}" }.toSet())
    }

    suspend fun chapterCount(mangaId: Long): Int =
        Injekt.get<GetChaptersByMangaId>().await(mangaId).size

    /** Starts a "until [newChapters] new chapters" timer, replacing any date timer */
    suspend fun startChapters(mangaId: Long, newChapters: Int) {
        val base = chapterCount(mangaId)
        timers().set(parse(timers().get()).filterKeys { it != mangaId }.map { (id, at) -> "$id:$at" }.toSet())
        saveGoals(chapterGoals() + (mangaId to (newChapters to base)))
        ready().set(ready().get() - mangaId.toString())
    }

    fun isReady(mangaId: Long): Boolean = mangaId.toString() in ready().get()

    /** Starts (or restarts) a timer; [weeks] counts from now */
    fun start(mangaId: Long, weeks: Int) {
        val due = LocalDateTime.now()
            .plusWeeks(weeks.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        saveGoals(chapterGoals() - mangaId)
        val map = parse(timers().get()).toMutableMap()
        map[mangaId] = due
        timers().set(map.map { (id, at) -> "$id:$at" }.toSet())
        ready().set(ready().get() - mangaId.toString())
    }

    fun cancel(mangaId: Long) {
        saveGoals(chapterGoals() - mangaId)
        timers().set(parse(timers().get()).filterKeys { it != mangaId }.map { (id, at) -> "$id:$at" }.toSet())
        ready().set(ready().get() - mangaId.toString())
    }

    /** Called when the series is opened in the reader: it's been eaten */
    fun served(mangaId: Long) {
        if (isReady(mangaId)) {
            ready().set(ready().get() - mangaId.toString())
            EggikuAchievements.bump(EggikuAchievements.COUNT_COOKED)
        }
    }

    /** Finishes any timers that are due: notifies and moves the series to Continue reading */
    suspend fun checkDue(context: Context, now: Long = System.currentTimeMillis()) {
        val timers = parse(timers().get())
        val dueByDate = timers.filterValues { it <= now }.keys
        // Chapter goals are done once enough new chapters have come in
        val goals = chapterGoals()
        val dueByChapters = goals.filter { (id, goal) ->
            try {
                chapterCount(id) - goal.second >= goal.first
            } catch (_: Exception) {
                false
            }
        }.keys
        val due = dueByDate + dueByChapters
        if (due.isEmpty()) return
        timers().set(timers.filterKeys { it !in due }.map { (id, at) -> "$id:$at" }.toSet())
        saveGoals(goals - dueByChapters)
        ready().set(ready().get() + due.map { it.toString() })

        val getManga = Injekt.get<GetManga>()
        due.forEach { mangaId ->
            val title = getManga.await(mangaId)?.title ?: return@forEach
            val intent = PendingIntent.getActivity(
                context,
                mangaId.hashCode(),
                Intent(context, MainActivity::class.java)
                    .setAction(Constants.SHORTCUT_MANGA)
                    .putExtra(Constants.MANGA_EXTRA, mangaId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.notify(ID_BASE - (mangaId % 1000).toInt(), Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(context.stringResource(KMR.strings.cook_done_title))
                setContentText(context.stringResource(KMR.strings.cook_done_text, title))
                setContentIntent(intent)
                setAutoCancel(true)
            }
        }
    }

    private const val ID_BASE = -2000
}
// KMK <--
