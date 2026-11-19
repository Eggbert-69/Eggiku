package eu.kanade.tachiyomi.data.search

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import exh.log.xLogE
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Normalizer
import java.util.UUID

// KMK -->
/** Eggiku search settings shared by global search and its helpers */
object EggikuSearchPrefs {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Seconds each source gets before it's marked as too slow (0 = no limit) */
    fun timeoutSeconds(): Preference<Int> = preferenceStore.getInt("eggiku_search_timeout", 15)

    /** Leave out sources that failed or timed out 3 times in a row (for a day) */
    fun skipFailing(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_search_skip_failing", true)

    /** Also search the series' other names from AniList */
    fun altTitles(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_search_alt_titles", false)

    /** One card per series instead of one row per source */
    fun merged(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_search_merged", false)

    /** Show matches from your library, history and Egg Drops above the sources */
    fun includeMine(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_search_mine", true)

    /** Free key from saucenao.com, for find by picture */
    fun sauceNaoKey(): Preference<String> = preferenceStore.getString("eggiku_saucenao_key", "")
}

/** Eggiku search groups: named sets of sources to search together, like "English" or "Raws" */
object EggikuSearchGroups {

    @Serializable
    data class Group(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val sourceIds: List<Long> = emptyList(),
    )

    /** Built in: sources that answered quickly in recent searches */
    const val FAST = "eggiku:fast"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Group.serializer())
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun pref(): Preference<String> = preferenceStore.getString("eggiku_search_groups", "[]")

    fun parse(raw: String): List<Group> = try {
        json.decodeFromString(serializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    fun all(): List<Group> = parse(pref().get())

    fun save(groups: List<Group>) = pref().set(json.encodeToString(serializer, groups))

    fun upsert(group: Group) {
        val current = all()
        save(if (current.any { it.id == group.id }) current.map { if (it.id == group.id) group else it } else current + group)
    }

    fun delete(id: String) = save(all().filterNot { it.id == id })

    /** The chosen group in global search (null = the usual Pinned / All) */
    fun selected(): Preference<String> = preferenceStore.getString("eggiku_search_group_selected", "")
}

/**
 * Eggiku: how each source has been doing in searches. Keeps a running average of how long it takes
 * to answer and how many times in a row it has failed, so slow or broken sources can be skipped
 * and fast ones searched first.
 */
object EggikuSourceHealth {

    @Serializable
    data class Health(
        val avgMs: Long = 0,
        val samples: Int = 0,
        val failuresInARow: Int = 0,
        val lastFailureAt: Long = 0,
    )

    const val FAILS_TO_SKIP = 3
    private const val SKIP_FOR_MS = 24 * 60 * 60 * 1000L
    const val FAST_MS = 4_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(Long.serializer(), Health.serializer())
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private fun pref() = preferenceStore.getString("eggiku_source_health", "{}")

    private val lock = Any()
    private var cache: MutableMap<Long, Health>? = null

    private fun load(): MutableMap<Long, Health> {
        cache?.let { return it }
        val loaded = try {
            json.decodeFromString(serializer, pref().get()).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
        cache = loaded
        return loaded
    }

    fun get(sourceId: Long): Health = synchronized(lock) { load()[sourceId] ?: Health() }

    fun all(): Map<Long, Health> = synchronized(lock) { load().toMap() }

    fun record(sourceId: Long, millis: Long, ok: Boolean) {
        synchronized(lock) {
            val map = load()
            val old = map[sourceId] ?: Health()
            map[sourceId] = if (ok) {
                val samples = (old.samples + 1).coerceAtMost(20)
                Health(
                    avgMs = if (old.samples == 0) millis else old.avgMs + (millis - old.avgMs) / samples,
                    samples = samples,
                    failuresInARow = 0,
                    lastFailureAt = old.lastFailureAt,
                )
            } else {
                old.copy(failuresInARow = old.failuresInARow + 1, lastFailureAt = System.currentTimeMillis())
            }
        }
    }

    /** Writes the numbers to storage; call when a search finishes */
    fun save() {
        val snapshot = synchronized(lock) { load().toMap() }
        pref().set(json.encodeToString(serializer, snapshot))
    }

    fun shouldSkip(sourceId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val health = get(sourceId)
        return health.failuresInARow >= FAILS_TO_SKIP && now - health.lastFailureAt < SKIP_FOR_MS
    }

    fun isFast(sourceId: Long): Boolean {
        val health = get(sourceId)
        return health.samples > 0 && health.avgMs <= FAST_MS && health.failuresInARow == 0
    }

    /** Lets a skipped source back in, e.g. from the "search them anyway" chip */
    fun forgive(sourceIds: Collection<Long>) {
        synchronized(lock) {
            val map = load()
            sourceIds.forEach { id -> map[id]?.let { map[id] = it.copy(failuresInARow = 0) } }
        }
        save()
    }
}

/** Eggiku: helpers for matching titles and tags across sources */
object EggikuSearchText {

    private val articles = Regex("""^(the|a|an)\s+""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    /** "The Villainess Turns the Hourglass!" and "villainess turns the hourglass" become the same key */
    fun titleKey(title: String): String {
        val folded = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("""\p{Mn}+"""), "")
            .trim()
            .replace(articles, "")
        return folded.replace(nonWord, "")
    }

    fun tagKey(tag: String): String = tag.lowercase().replace(nonWord, "").removeSuffix("s")

    /**
     * Turns on the genre / tag in a source's filters that matches [tag]. Returns false if the source
     * has nothing that matches, so it can be left out of a tag search.
     */
    fun applyTag(filters: FilterList, tag: String): Boolean {
        val want = tagKey(tag)
        if (want.isEmpty()) return false
        var found = false
        fun visit(filter: Filter<*>) {
            when (filter) {
                is Filter.Group<*> -> filter.state.forEach { child -> if (child is Filter<*>) visit(child) }
                is Filter.CheckBox -> if (tagKey(filter.name) == want) {
                    filter.state = true
                    found = true
                }
                is Filter.TriState -> if (tagKey(filter.name) == want) {
                    filter.state = Filter.TriState.STATE_INCLUDE
                    found = true
                }
                is Filter.Select<*> -> {
                    // Only a select that already has a genre-like name, so a "Sort" list never matches
                    val name = filter.name.lowercase()
                    if (!found && listOf("genre", "tag", "categor", "theme").any { it in name }) {
                        val index = filter.values.indexOfFirst { tagKey(it.toString()) == want }
                        if (index >= 0) {
                            filter.state = index
                            found = true
                        }
                    }
                }
                else -> Unit
            }
        }
        filters.forEach { visit(it) }
        return found
    }
}

/** Eggiku: other names for a series (English, romaji, native) from AniList, to search them too */
object EggikuAltTitles {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, List<String>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 30
    }

    private const val QUERY = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: MANGA) {
            title { romaji english native }
            synonyms
          }
        }
    """

    /** Up to [max] other names, never including the query itself; empty if AniList has nothing */
    suspend fun find(query: String, max: Int = 3): List<String> = withIOContext {
        val key = EggikuSearchText.titleKey(query)
        synchronized(cache) { cache[key] }?.let { return@withIOContext it }
        val titles = try {
            val body = buildJsonObject {
                put("query", QUERY)
                put("variables", buildJsonObject { put("search", query) })
            }.toString().toRequestBody("application/json".toMediaType())
            val text = Injekt.get<NetworkHelper>().client
                .newCall(POST("https://graphql.anilist.co", body = body))
                .awaitSuccess()
                .use { it.body.string() }
            val media = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("Media") as? JsonObject
            if (media == null) {
                emptyList()
            } else {
                val title = media["title"]?.jsonObject
                val names = listOf("english", "romaji", "native").mapNotNull { title?.get(it)?.jsonPrimitive?.contentOrNull } +
                    ((media["synonyms"] as? JsonArray)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty())
                names
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && EggikuSearchText.titleKey(it) != key }
                    .distinctBy { EggikuSearchText.titleKey(it) }
                    .take(max)
            }
        } catch (e: Exception) {
            // Not cached, so the next search tries again
            xLogE("AniList alternate titles failed", e)
            return@withIOContext emptyList()
        }
        synchronized(cache) { cache[key] = titles }
        titles
    }
}
// KMK <--
