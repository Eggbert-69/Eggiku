package eu.kanade.tachiyomi.data.achievements

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku friend compare: your tiers packed into a short code (or an eggiku://compare link) that a
 * friend opens in Eggiku to see the two of you side by side. Only achievement tiers and badge
 * names go in it; nothing about your library.
 */
object EggikuCompare {

    @Serializable
    data class Profile(
        val v: Int = 1,
        val name: String = "",
        /** ladder key -> tiers earned */
        val tiers: Map<String, Int> = emptyMap(),
        /** one-off badge keys earned */
        val badges: List<String> = emptyList(),
        val earned: Int = 0,
        val total: Int = 0,
    )

    private const val PREFIX = "EGG1-"
    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun nickname() = preferenceStore.getString("eggiku_compare_name", "")

    fun mine(facts: EggikuAchievements.Facts): Profile {
        val earned = EggikuAchievements.earned()
        return Profile(
            name = nickname().get(),
            tiers = EggikuAchievements.ladders.associate { it.key to it.tiersEarned(facts) },
            badges = EggikuAchievements.oneOffs.filter { it.earnedKey in earned }.map { it.key },
            earned = earned.size.coerceAtMost(EggikuAchievements.totalCount),
            total = EggikuAchievements.totalCount,
        )
    }

    fun encode(profile: Profile): String {
        val bytes = json.encodeToString(profile).toByteArray()
        return PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun link(profile: Profile) = "eggiku://compare?d=" + encode(profile)

    /** Reads a code, a link, or a message containing either */
    fun decode(text: String?): Profile? {
        if (text.isNullOrBlank()) return null
        val code = Regex("""EGG1-[A-Za-z0-9_\-]+""").find(text)?.value ?: return null
        return try {
            val bytes = Base64.decode(code.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            json.decodeFromString<Profile>(String(bytes))
        } catch (_: Exception) {
            null
        }
    }

    fun fromUri(uri: Uri?): Profile? {
        if (uri == null || uri.scheme != "eggiku" || uri.host != "compare") return null
        return decode(uri.getQueryParameter("d"))
    }
}
// KMK <--
