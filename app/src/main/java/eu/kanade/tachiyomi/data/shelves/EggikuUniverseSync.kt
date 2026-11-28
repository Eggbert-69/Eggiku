package eu.kanade.tachiyomi.data.shelves

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.EggikuCoolEgg
import eu.kanade.tachiyomi.data.duplicates.EggikuDuplicates
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.main.EggikuOpen
import eu.kanade.tachiyomi.util.system.notify
import exh.log.xLogE
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mihon.domain.manga.model.toDomainManga
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

// KMK -->

/**
 * Eggiku Universe, done for you. Each series in the library is looked up on AniList (by your
 * AniList tracker if you have one, otherwise by title), and its official sequels, prequels,
 * spin-offs and side stories are linked up. Ones already in the library get linked; ones that
 * aren't get found in the same source and added to the library in their own "Universe" category,
 * so your own categories are never touched.
 */
object EggikuUniverseSync {

    private const val API_URL = "https://graphql.anilist.co"
    private const val ANILIST_TRACKER_ID = 2L
    private const val MAL_TRACKER_ID = 1L
    private const val CATEGORY_NAME = "Universe"
    private const val ID_ADDED = -1923

    /** Series looked up per run, and how long until a series is looked up again */
    private const val PER_RUN = 20
    private val RECHECK_MS = TimeUnit.DAYS.toMillis(14)

    /** AniList allows about 30 requests a minute right now */
    private const val REQUEST_GAP_MS = 2_500L

    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun enabled() = preferenceStore.getBoolean("eggiku_universe_auto", true)

    /** Series id -> when it was last looked up */
    private fun checkedPref() = preferenceStore.getString(Preference.appStateKey("eggiku_universe_checked"), "")

    private fun lastRun() = preferenceStore.getLong(Preference.appStateKey("eggiku_universe_last_run"), 0L)

    @Serializable
    private data class Checked(val at: Map<Long, Long> = emptyMap())

    private data class Related(
        val kind: EggikuShelves.Kind,
        val titles: List<String>,
    )

    /** Runs at most once a day; [force] skips that (the "Look now" button) */
    suspend fun run(context: Context, force: Boolean = false): Int = withIOContext {
        if (!enabled().get() && !force) return@withIOContext 0
        val now = System.currentTimeMillis()
        if (!force && now - lastRun().get() < TimeUnit.HOURS.toMillis(20)) return@withIOContext 0
        // Network lookups and adding series can wait for a cool phone
        if (!force && EggikuCoolEgg.current(context) != EggikuCoolEgg.Heat.COOL) return@withIOContext 0
        lastRun().set(now)

        val checked = try {
            json.decodeFromString<Checked>(checkedPref().get().ifBlank { "{}" }).at.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
        val library = Injekt.get<GetLibraryManga>().await().distinctBy { it.id }
        val byTitle = HashMap<String, Long>()
        library.forEach { byTitle[EggikuDuplicates.normalize(it.manga.title)] = it.id }

        val todo = library
            .filter { !it.manga.isLocal() && it.manga.source != MERGED_SOURCE_ID }
            .filter { force || now - (checked[it.id] ?: 0L) > RECHECK_MS }
            .sortedByDescending { it.lastRead }
            .take(PER_RUN)

        val added = mutableListOf<String>()
        for (series in todo) {
            checked[series.id] = now
            val related = try {
                relationsFor(series)
            } catch (e: Exception) {
                xLogE("Eggiku universe: lookup failed for ${series.manga.title}", e)
                delay(REQUEST_GAP_MS)
                continue
            }
            delay(REQUEST_GAP_MS)
            for (relation in related) {
                val keys = relation.titles.map { EggikuDuplicates.normalize(it) }.filter { it.length >= 3 }
                val existing = keys.firstNotNullOfOrNull { byTitle[it] }
                when {
                    existing == series.id -> Unit
                    existing != null -> {
                        if (existing !in EggikuShelves.universe(series.id)) {
                            EggikuShelves.link(series.id, existing, relation.kind)
                        }
                    }
                    else -> {
                        val newId = addFromSource(series, relation) ?: continue
                        EggikuShelves.link(series.id, newId, relation.kind)
                        keys.forEach { byTitle[it] = newId }
                        added += relation.titles.first()
                    }
                }
            }
        }
        checkedPref().set(json.encodeToString(Checked(checked)))

        if (added.isNotEmpty()) {
            context.notify(ID_ADDED, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(context, R.color.ic_launcher))
                setContentTitle(context.pluralStringResource(KMR.plurals.universe_added_title, added.size, added.size))
                setContentText(added.take(3).joinToString())
                setContentIntent(EggikuOpen.pendingIntent(context, ID_ADDED, EggikuOpen.SHELVES, EggikuOpen.SHELF_SPINOFFS))
                setAutoCancel(true)
            }
        }
        added.size
    }

    /** Official relations of a series from AniList */
    private suspend fun relationsFor(series: LibraryManga): List<Related> {
        val tracks = Injekt.get<GetTracks>().await(series.id)
        val anilistId = tracks.firstOrNull { it.trackerId == ANILIST_TRACKER_ID }?.remoteId
        val malId = tracks.firstOrNull { it.trackerId == MAL_TRACKER_ID }?.remoteId
        val media = when {
            anilistId != null -> query(byId = anilistId)
            malId != null -> query(byMalId = malId)
            else -> query(search = series.manga.title)
                // A title search must really be this series, not just something with a similar name
                ?.takeIf { media -> titlesOf(media).any { sameTitle(it, series.manga.title) } }
        } ?: return emptyList()

        val edges = media["relations"]?.jsonObject?.get("edges")?.jsonArray ?: return emptyList()
        return edges.mapNotNull { edge ->
            val obj = edge.jsonObject
            val kind = when (obj["relationType"]?.jsonPrimitive?.contentOrNull) {
                "SEQUEL" -> EggikuShelves.Kind.SEQUEL
                "PREQUEL" -> EggikuShelves.Kind.PREQUEL
                "SPIN_OFF" -> EggikuShelves.Kind.SPINOFF
                "SIDE_STORY" -> EggikuShelves.Kind.SIDE_STORY
                else -> return@mapNotNull null
            }
            val node = obj["node"]?.jsonObject ?: return@mapNotNull null
            if (node["type"]?.jsonPrimitive?.contentOrNull != "MANGA") return@mapNotNull null
            if (node["format"]?.jsonPrimitive?.contentOrNull == "NOVEL") return@mapNotNull null
            val titles = titlesOf(node)
            if (titles.isEmpty()) return@mapNotNull null
            Related(kind, titles)
        }
    }

    private suspend fun query(byId: Long? = null, byMalId: Long? = null, search: String? = null): JsonObject? {
        val filter = when {
            byId != null -> "id: \$id"
            byMalId != null -> "idMal: \$id"
            else -> "search: \$search"
        }
        val variablesDecl = if (search != null) "\$search: String" else "\$id: Int"
        val query = """
            query ($variablesDecl) {
              Media($filter, type: MANGA) {
                title { romaji english native }
                synonyms
                relations {
                  edges {
                    relationType
                    node { type format title { romaji english native } synonyms }
                  }
                }
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                if (search != null) put("search", search) else put("id", (byId ?: byMalId)!!.toInt())
            }
        }
        val client = Injekt.get<NetworkHelper>().client
        val body = client.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
            .awaitSuccess()
            .use { it.body.string() }
        return json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Media") as? JsonObject
    }

    private fun titlesOf(media: JsonObject): List<String> {
        val title = media["title"]?.jsonObject
        val main = listOf("english", "romaji", "native").mapNotNull { title?.get(it)?.jsonPrimitive?.contentOrNull }
        val synonyms = (media["synonyms"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        return (main + synonyms).filter { it.isNotBlank() }.distinct()
    }

    private fun sameTitle(a: String, b: String) = EggikuDuplicates.normalize(a) == EggikuDuplicates.normalize(b)

    /** Finds the related series in the parent's own source and adds it to the Universe category */
    private suspend fun addFromSource(parent: LibraryManga, relation: Related): Long? {
        val source = Injekt.get<SourceManager>().get(parent.manga.source) as? CatalogueSource ?: return null
        for (title in relation.titles.take(3)) {
            val results = try {
                source.getSearchManga(1, title, source.getFilterList()).mangas
            } catch (_: Exception) {
                null
            } ?: continue
            delay(1_500)
            val match = results.take(20).firstOrNull { result -> relation.titles.any { sameTitle(it, result.title) } }
                ?: continue
            return addToLibrary(match, source.id)
        }
        return null
    }

    private suspend fun addToLibrary(sManga: SManga, sourceId: Long): Long? {
        val manga = Injekt.get<NetworkToLocalManga>().invoke(sManga.toDomainManga(sourceId), updateInfo = false)
        if (manga.favorite) return manga.id
        Injekt.get<UpdateManga>().awaitUpdateFavorite(manga.id, true)
        val categoryId = universeCategoryId() ?: return manga.id
        Injekt.get<SetMangaCategories>().await(manga.id, listOf(categoryId))
        return manga.id
    }

    private suspend fun universeCategoryId(): Long? {
        val getCategories = Injekt.get<GetCategories>()
        getCategories.await().firstOrNull { it.name == CATEGORY_NAME }?.let { return it.id }
        Injekt.get<CreateCategoryWithName>().await(CATEGORY_NAME)
        return getCategories.await().firstOrNull { it.name == CATEGORY_NAME }?.id
    }
}
// KMK <--
