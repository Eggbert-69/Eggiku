package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.SetMangaCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: remembers recent migrations for a week so the last one (a single series or a whole
 * batch) can be undone: the old entries go back in the library with their categories, and new
 * entries that weren't in the library before leave it again.
 */
object EggikuMigrationUndo {

    @Serializable
    data class Entry(
        val batch: Long,
        val oldId: Long,
        val newId: Long,
        val replaced: Boolean,
        val newWasFavorite: Boolean,
        val oldCategories: List<Long>,
        val at: Long,
    )

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Entry.serializer())
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private fun pref() = preferenceStore.getString("eggiku_migration_undo", "[]")

    /** Set by a batch migration so all its series undo together; 0 means each is its own */
    @Volatile var currentBatch: Long = 0L

    private fun load(now: Long = System.currentTimeMillis()): List<Entry> = try {
        json.decodeFromString(serializer, pref().get()).filter { now - it.at < WEEK_MS }
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun record(oldId: Long, newId: Long, replaced: Boolean, newWasFavorite: Boolean, oldCategories: List<Long>) {
        val now = System.currentTimeMillis()
        val batch = currentBatch.takeIf { it != 0L } ?: now
        val entries = load(now) + Entry(batch, oldId, newId, replaced, newWasFavorite, oldCategories, now)
        pref().set(json.encodeToString(serializer, entries.takeLast(500)))
    }

    /** How many series the last migration moved (0 = nothing to undo) */
    fun lastBatchSize(): Int {
        val entries = load()
        val last = entries.maxOfOrNull { it.batch } ?: return 0
        return entries.count { it.batch == last }
    }

    suspend fun undoLast(): Int = withIOContext {
        val entries = load()
        val last = entries.maxOfOrNull { it.batch } ?: return@withIOContext 0
        val batch = entries.filter { it.batch == last }
        val updateManga = Injekt.get<UpdateManga>()
        val setCategories = Injekt.get<SetMangaCategories>()
        batch.forEach { entry ->
            if (entry.replaced) {
                updateManga.awaitUpdateFavorite(entry.oldId, true)
                if (entry.oldCategories.isNotEmpty()) setCategories.await(entry.oldId, entry.oldCategories)
            }
            if (!entry.newWasFavorite) updateManga.awaitUpdateFavorite(entry.newId, false)
        }
        pref().set(json.encodeToString(serializer, entries.filter { it.batch != last }))
        batch.size
    }
}
// KMK <--
