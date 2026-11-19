package eu.kanade.tachiyomi.data.drop

import android.net.Uri
import android.util.Base64
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import eu.kanade.tachiyomi.data.avatar.EggikuAvatar
import eu.kanade.tachiyomi.data.avatar.EggikuAvatars
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku Egg Drop: send a series to a friend with your stars and a short note. The drop is packed
 * into an eggiku://drop link (and an EGGD1- code, for chat apps that don't make the link tappable),
 * so it travels through any messaging app with no server. Received drops are kept in a small inbox.
 *
 * The drop carries the source id and the series' path on that source, so a friend with the same
 * extension opens the exact series straight away; anyone else gets a search by title.
 */
object EggikuDrops {

    @Serializable
    data class Drop(
        val v: Int = 1,
        /** Sender's nickname (the friend compare nickname) */
        val from: String = "",
        val avatar: EggikuAvatar? = null,
        val sourceId: Long = 0L,
        val sourceName: String = "",
        /** The series' url on its source, as the source stores it */
        val url: String = "",
        /** Web link, for searching or opening when the source isn't installed */
        val webUrl: String? = null,
        val title: String = "",
        val cover: String? = null,
        /** 0 = no rating */
        val stars: Int = 0,
        val note: String = "",
        val sentAt: Long = 0L,
    )

    @Serializable
    data class Received(
        val drop: Drop,
        val receivedAt: Long,
        val seen: Boolean = false,
    ) {
        /** Same series from the same friend counts as one drop */
        val key: String get() = "${drop.from}|${drop.sourceId}|${drop.url}"
    }

    const val NOTE_MAX = 280
    private const val INBOX_MAX = 100
    private const val PREFIX = "EGGD1-"
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private val codeRegex = Regex("""EGGD1-[A-Za-z0-9_\-]+""")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private fun inboxPref(): Preference<String> = preferenceStore.getString("eggiku_drop_inbox", "[]")

    fun create(
        manga: Manga,
        sourceName: String,
        webUrl: String?,
        stars: Int,
        note: String,
    ) = Drop(
        from = EggikuCompare.nickname().get().trim(),
        avatar = runCatching { EggikuAvatars.mine() }.getOrNull(),
        sourceId = manga.source,
        sourceName = sourceName,
        url = manga.url,
        webUrl = webUrl,
        title = manga.title,
        // Local file covers mean nothing on a friend's phone
        cover = manga.thumbnailUrl?.takeIf { it.startsWith("http") },
        stars = stars.coerceIn(0, 5),
        note = note.trim().take(NOTE_MAX),
        sentAt = System.currentTimeMillis(),
    )

    fun encode(drop: Drop): String {
        val bytes = json.encodeToString(drop).toByteArray()
        return PREFIX + Base64.encodeToString(bytes, FLAGS)
    }

    fun link(drop: Drop) = "eggiku://drop?d=" + encode(drop)

    /** Reads a code, a link, or a whole chat message containing either */
    fun decode(text: String?): Drop? {
        if (text.isNullOrBlank()) return null
        val code = codeRegex.find(text)?.value ?: return null
        return try {
            val bytes = Base64.decode(code.removePrefix(PREFIX), FLAGS)
            json.decodeFromString<Drop>(String(bytes)).takeIf { it.title.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun fromUri(uri: Uri?): Drop? {
        if (uri == null || uri.scheme != "eggiku" || uri.host != "drop") return null
        return decode(uri.getQueryParameter("d"))
    }

    // Inbox

    private fun parse(raw: String): List<Received> = try {
        json.decodeFromString<List<Received>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    fun inbox(): List<Received> = parse(inboxPref().get())

    fun inboxChanges(): Flow<List<Received>> = inboxPref().changes().map(::parse)

    private fun save(list: List<Received>) {
        inboxPref().set(json.encodeToString(list.sortedByDescending { it.receivedAt }.take(INBOX_MAX)))
    }

    /** Call after the share sheet opens, for the Egg Drops achievement ladder */
    fun countSent() = EggikuAchievements.bump(EggikuAchievements.COUNT_DROPS_SENT)

    /** Adds a drop to the inbox; opening the same drop twice doesn't add it twice */
    fun receive(drop: Drop) {
        val entry = Received(drop, System.currentTimeMillis())
        val current = inbox()
        val existing = current.firstOrNull { it.key == entry.key }
        if (existing != null && existing.drop.sentAt >= drop.sentAt) return
        if (existing == null) EggikuAchievements.bump(EggikuAchievements.COUNT_DROPS_RECEIVED)
        // A newer drop of the same series (say, a new note) replaces the old one and shows as new
        save(current.filterNot { it.key == entry.key } + entry)
    }

    fun markSeen(key: String) {
        val current = inbox()
        if (current.none { it.key == key && !it.seen }) return
        save(current.map { if (it.key == key) it.copy(seen = true) else it })
    }

    fun markAllSeen() {
        val current = inbox()
        if (current.all { it.seen }) return
        save(current.map { it.copy(seen = true) })
    }

    fun remove(key: String) = save(inbox().filterNot { it.key == key })
}
// KMK <--
