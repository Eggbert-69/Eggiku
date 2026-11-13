package eu.kanade.tachiyomi.data.smart

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.cook.EggikuCookTimer
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.tags.EggikuTags
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

// KMK -->
/**
 * Eggiku smart categories. Each one is a live view of the library, worked out from rules every
 * time it's shown. Nothing is ever moved: a series stays in its real categories and simply shows
 * up in any smart category it matches.
 */
object EggikuSmart {

    /** The id of the Smart tab in the library. Never a real category id. */
    const val TAB_ID = -1919L

    @Serializable
    enum class Type {
        UNREAD_AT_LEAST,
        UNREAD_AT_MOST,
        STARTED,
        NEVER_READ,
        LAST_READ_WITHIN_DAYS,
        LAST_READ_OVER_DAYS,
        UPDATED_WITHIN_DAYS,
        NO_UPDATE_DAYS,
        ADDED_WITHIN_DAYS,
        STATUS_ONGOING,
        STATUS_COMPLETED,
        STATUS_HIATUS,
        NOT_COMPLETED,
        IN_CATEGORY,
        NOT_IN_CATEGORY,
        GENRE_CONTAINS,
        RATING_AT_LEAST,
        HAS_TAG,
        HAS_DOWNLOADS,
        COOKING,
        TRACKED,
        ;

        /** What kind of value the condition needs */
        val input: Input
            get() = when (this) {
                UNREAD_AT_LEAST, UNREAD_AT_MOST, LAST_READ_WITHIN_DAYS, LAST_READ_OVER_DAYS,
                UPDATED_WITHIN_DAYS, NO_UPDATE_DAYS, ADDED_WITHIN_DAYS, RATING_AT_LEAST,
                -> Input.NUMBER
                IN_CATEGORY, NOT_IN_CATEGORY -> Input.CATEGORY
                GENRE_CONTAINS -> Input.TEXT
                HAS_TAG -> Input.TAG
                else -> Input.NONE
            }
    }

    enum class Input { NONE, NUMBER, TEXT, CATEGORY, TAG }

    @Serializable
    enum class Sort { LAST_READ, MOST_UNREAD, LEAST_UNREAD, RECENTLY_UPDATED, OLDEST_UPDATE, TITLE, RATING, DATE_ADDED }

    @Serializable
    data class Condition(val type: Type, val number: Long = 0, val text: String = "")

    @Serializable
    data class Rule(
        val id: String,
        val name: String,
        val conditions: List<Condition>,
        val matchAll: Boolean = true,
        val sort: Sort = Sort.LAST_READ,
        val hidden: Boolean = false,
        val preset: Boolean = false,
    )

    /** Everything a rule can look at for one series */
    class Item(
        val manga: LibraryManga,
        val downloadCount: Long,
        val tracked: Boolean,
    )

    /** Shared lookups, read once per evaluation rather than once per series */
    class Context(
        val ratings: Map<Long, Int> = EggikuRatings.all(),
        val tags: Map<Long, List<String>> = EggikuTags.assignments(),
        val cooking: Set<Long> = EggikuCookTimer.parse(EggikuCookTimer.timers().get()).keys +
            EggikuCookTimer.chapterGoals().keys,
        val now: Long = System.currentTimeMillis(),
    )

    /** Built-in names, used while a ready-made smart category keeps an empty name */
    val presetNames: Map<String, StringResource> = mapOf(
        "binge" to KMR.strings.smart_binge,
        "nearly" to KMR.strings.smart_nearly,
        "dropped" to KMR.strings.smart_dropped,
        "stalled" to KMR.strings.smart_stalled,
        "finished_unread" to KMR.strings.smart_finished_unread,
        "top" to KMR.strings.smart_top,
        "fresh" to KMR.strings.smart_fresh,
        "offline" to KMR.strings.smart_offline,
    )

    val presets = listOf(
        Rule(
            "binge",
            "",
            listOf(Condition(Type.UNREAD_AT_LEAST, 20)),
            sort = Sort.MOST_UNREAD,
            preset = true,
        ),
        Rule(
            "nearly",
            "",
            listOf(Condition(Type.STARTED), Condition(Type.UNREAD_AT_LEAST, 1), Condition(Type.UNREAD_AT_MOST, 3)),
            sort = Sort.LAST_READ,
            preset = true,
        ),
        Rule(
            "dropped",
            "",
            listOf(Condition(Type.STARTED), Condition(Type.LAST_READ_OVER_DAYS, 90), Condition(Type.UNREAD_AT_LEAST, 1)),
            sort = Sort.LAST_READ,
            preset = true,
        ),
        Rule(
            "stalled",
            "",
            listOf(Condition(Type.NO_UPDATE_DAYS, 180), Condition(Type.NOT_COMPLETED)),
            sort = Sort.OLDEST_UPDATE,
            preset = true,
        ),
        Rule(
            "finished_unread",
            "",
            listOf(Condition(Type.STATUS_COMPLETED), Condition(Type.NEVER_READ)),
            sort = Sort.TITLE,
            preset = true,
        ),
        Rule(
            "top",
            "",
            listOf(Condition(Type.RATING_AT_LEAST, 4)),
            sort = Sort.RATING,
            preset = true,
        ),
        Rule(
            "fresh",
            "",
            listOf(Condition(Type.ADDED_WITHIN_DAYS, 30)),
            sort = Sort.DATE_ADDED,
            preset = true,
        ),
        Rule(
            "offline",
            "",
            listOf(Condition(Type.HAS_DOWNLOADS), Condition(Type.UNREAD_AT_LEAST, 1)),
            sort = Sort.LAST_READ,
            preset = true,
        ),
    )

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val json = Json { ignoreUnknownKeys = true }

    fun enabled() = preferenceStore.getBoolean("eggiku_smart_tab", true)

    fun rulesPref() = preferenceStore.getString("eggiku_smart_rules", "")

    fun parse(raw: String): List<Rule> = if (raw.isBlank()) {
        presets
    } else {
        try {
            json.decodeFromString<List<Rule>>(raw)
        } catch (_: Exception) {
            presets
        }
    }

    fun rules(): List<Rule> = parse(rulesPref().get())

    fun save(rules: List<Rule>) = rulesPref().set(json.encodeToString(rules))

    private fun days(n: Long) = TimeUnit.DAYS.toMillis(n)

    fun matches(condition: Condition, item: Item, ctx: Context): Boolean {
        val m = item.manga
        return when (condition.type) {
            Type.UNREAD_AT_LEAST -> m.unreadCount >= condition.number
            Type.UNREAD_AT_MOST -> m.unreadCount <= condition.number
            Type.STARTED -> m.hasStarted
            Type.NEVER_READ -> !m.hasStarted
            Type.LAST_READ_WITHIN_DAYS -> m.lastRead > 0 && ctx.now - m.lastRead <= days(condition.number)
            Type.LAST_READ_OVER_DAYS -> m.lastRead > 0 && ctx.now - m.lastRead > days(condition.number)
            Type.UPDATED_WITHIN_DAYS -> m.latestUpload > 0 && ctx.now - m.latestUpload <= days(condition.number)
            Type.NO_UPDATE_DAYS -> m.latestUpload > 0 && ctx.now - m.latestUpload > days(condition.number)
            Type.ADDED_WITHIN_DAYS -> m.manga.dateAdded > 0 && ctx.now - m.manga.dateAdded <= days(condition.number)
            Type.STATUS_ONGOING -> m.manga.status.toInt() == SManga.ONGOING
            Type.STATUS_COMPLETED -> m.manga.status.toInt() in setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED)
            Type.STATUS_HIATUS -> m.manga.status.toInt() == SManga.ON_HIATUS
            Type.NOT_COMPLETED -> m.manga.status.toInt() !in
                setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED, SManga.CANCELLED)
            Type.IN_CATEGORY -> condition.number in m.categories
            Type.NOT_IN_CATEGORY -> condition.number !in m.categories
            Type.GENRE_CONTAINS -> condition.text.isNotBlank() &&
                m.manga.genre.orEmpty().any { it.contains(condition.text.trim(), ignoreCase = true) }
            Type.RATING_AT_LEAST -> (ctx.ratings[m.id] ?: 0) >= condition.number
            Type.HAS_TAG -> condition.text in ctx.tags[m.id].orEmpty()
            Type.HAS_DOWNLOADS -> item.downloadCount > 0
            Type.COOKING -> m.id in ctx.cooking
            Type.TRACKED -> item.tracked
        }
    }

    fun matches(rule: Rule, item: Item, ctx: Context): Boolean {
        if (rule.conditions.isEmpty()) return false
        return if (rule.matchAll) {
            rule.conditions.all { matches(it, item, ctx) }
        } else {
            rule.conditions.any { matches(it, item, ctx) }
        }
    }

    fun <T> sort(rule: Rule, items: List<T>, manga: (T) -> LibraryManga, ctx: Context): List<T> = when (rule.sort) {
        Sort.LAST_READ -> items.sortedByDescending { manga(it).lastRead }
        Sort.MOST_UNREAD -> items.sortedByDescending { manga(it).unreadCount }
        Sort.LEAST_UNREAD -> items.sortedBy { manga(it).unreadCount }
        Sort.RECENTLY_UPDATED -> items.sortedByDescending { manga(it).latestUpload }
        Sort.OLDEST_UPDATE -> items.sortedBy { manga(it).latestUpload }
        Sort.TITLE -> items.sortedBy { manga(it).manga.title.lowercase() }
        Sort.RATING -> items.sortedWith(
            compareByDescending<T> { ctx.ratings[manga(it).id] ?: 0 }.thenBy { manga(it).manga.title.lowercase() },
        )
        Sort.DATE_ADDED -> items.sortedByDescending { manga(it).manga.dateAdded }
    }
}
// KMK <--
