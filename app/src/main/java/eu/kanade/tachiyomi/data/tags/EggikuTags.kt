package eu.kanade.tachiyomi.data.tags

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku tags: your own coloured labels ("comfort read", "for the gym") on any series. They only
 * label things; categories are never touched. Kept as two small sets: the tags you've made, and
 * which series carry which tag.
 */
object EggikuTags {

    data class Tag(val name: String, val color: Long)

    /** Colours offered when making a tag */
    val palette = listOf(
        0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF4DB6AC,
        0xFF64B5F6, 0xFF9575CD, 0xFFF06292, 0xFFA1887F, 0xFF90A4AE,
    )

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** "order|color|name" */
    fun definitionsPref(): Preference<Set<String>> = preferenceStore.getStringSet("eggiku_tags", emptySet())

    /** "mangaId|name" */
    fun assignmentsPref(): Preference<Set<String>> = preferenceStore.getStringSet("eggiku_tag_assignments", emptySet())

    fun parseDefinitions(entries: Set<String>): List<Tag> = entries.mapNotNull {
        val parts = it.split('|', limit = 3)
        if (parts.size < 3) return@mapNotNull null
        Triple(parts[0].toIntOrNull() ?: 0, parts[1].toLongOrNull() ?: palette.first(), parts[2])
    }.sortedBy { it.first }.map { Tag(it.third, it.second) }

    fun parseAssignments(entries: Set<String>): Map<Long, List<String>> = entries.mapNotNull {
        val id = it.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
        id to it.substringAfter('|')
    }.groupBy({ it.first }, { it.second })

    fun all(): List<Tag> = parseDefinitions(definitionsPref().get())

    fun assignments(): Map<Long, List<String>> = parseAssignments(assignmentsPref().get())

    fun create(name: String, color: Long) {
        val clean = name.trim().replace("|", "")
        if (clean.isEmpty() || all().any { it.name.equals(clean, ignoreCase = true) }) return
        val order = all().size
        definitionsPref().set(definitionsPref().get() + "$order|$color|$clean")
    }

    fun delete(name: String) {
        definitionsPref().set(definitionsPref().get().filterNot { it.substringAfter('|').substringAfter('|') == name }.toSet())
        assignmentsPref().set(assignmentsPref().get().filterNot { it.substringAfter('|') == name }.toSet())
    }

    fun toggle(mangaId: Long, name: String) {
        val entry = "$mangaId|$name"
        val current = assignmentsPref().get()
        assignmentsPref().set(if (entry in current) current - entry else current + entry)
    }

    /** Moves tags to a new entry, e.g. after a migration or duplicate merge */
    fun move(fromId: Long, toId: Long) {
        val names = assignments()[fromId].orEmpty()
        if (names.isEmpty()) return
        assignmentsPref().set(assignmentsPref().get() + names.map { "$toId|$it" })
    }
}
// KMK <--
