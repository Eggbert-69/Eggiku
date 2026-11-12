package eu.kanade.tachiyomi.data.eggikuupdate

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: checks the Eggiku GitHub releases for newer builds, and Komikku's for its latest
 * stable version, so the More tab can show what you have against what's out.
 */
object EggikuUpdates {

    private const val EGGIKU_RELEASES = "https://api.github.com/repos/Eggbert-69/Eggiku/releases?per_page=40"
    private const val KOMIKKU_LATEST = "https://api.github.com/repos/komikku-app/komikku/releases/latest"
    private val BUILD_TAG = Regex("""^build-(.+)-(\d+)$""")

    @Serializable
    private data class GhAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        @SerialName("published_at") val published: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    data class Build(
        val branch: String,
        val number: Int,
        val title: String,
        val published: String,
        val apkUrl: String,
        val apkName: String,
    )

    data class Status(
        /** The build that's installed, if it came from these releases */
        val installed: Build?,
        /** Newest build on the installed build's branch (or any branch, see [anyBranch]) */
        val latest: Build?,
        val anyBranch: Boolean,
        /** Komikku's latest stable version, e.g. "1.14.1" */
        val komikkuLatest: String?,
    ) {
        val updateAvailable: Boolean
            get() = latest != null && installed?.let { it != latest && latestIsNewer(it, latest) } ?: true

        private fun latestIsNewer(installed: Build, latest: Build): Boolean =
            installed.branch != latest.branch || latest.number > installed.number
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Follow every branch's builds instead of only the one installed */
    fun followAllBranches() = preferenceStore.getBoolean("eggiku_update_all_branches", false)

    fun autoCheck() = preferenceStore.getBoolean("eggiku_update_auto_check", true)

    /** Tag of the newest build seen, so the More tab can show a dot until you look */
    fun lastSeenTag() = preferenceStore.getString(Preference.appStateKey("eggiku_update_seen"), "")

    fun availableTag() = preferenceStore.getString(Preference.appStateKey("eggiku_update_available"), "")

    /** Komikku version this Eggiku is built on */
    val baseVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    suspend fun check(): Status = withIOContext {
        val client = Injekt.get<NetworkHelper>().client
        val releases = client.newCall(GET(EGGIKU_RELEASES)).awaitSuccess().use { response ->
            json.decodeFromString(ListSerializer(GhRelease.serializer()), response.body.string())
        }
        val builds = releases.mapNotNull { release ->
            val match = BUILD_TAG.find(release.tag) ?: return@mapNotNull null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@mapNotNull null
            Build(
                branch = match.groupValues[1],
                number = match.groupValues[2].toInt(),
                title = release.name ?: release.tag,
                published = release.published.orEmpty(),
                apkUrl = apk.url,
                apkName = apk.name,
            )
        }
        val sha = BuildConfig.COMMIT_SHA
        val installed = builds.firstOrNull { build ->
            val apkSha = build.apkName.substringAfterLast('-').removeSuffix(".apk")
            sha.length >= 7 && (apkSha.startsWith(sha) || sha.startsWith(apkSha))
        }
        val all = followAllBranches().get() || installed == null
        val latest = if (all) {
            builds.maxByOrNull { it.published }
        } else {
            builds.filter { it.branch == installed!!.branch }.maxByOrNull { it.number }
        }
        val komikku = try {
            client.newCall(GET(KOMIKKU_LATEST)).awaitSuccess().use { response ->
                json.decodeFromString(GhRelease.serializer(), response.body.string()).tag.removePrefix("v")
            }
        } catch (_: Exception) {
            null
        }
        val status = Status(installed, latest, all, komikku)
        availableTag().set(if (status.updateAvailable) latest?.let { "build-${it.branch}-${it.number}" }.orEmpty() else "")
        status
    }
}
// KMK <--
