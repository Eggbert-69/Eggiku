package eu.kanade.tachiyomi.data.avatar

import android.content.Context
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import exh.log.xLogE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku avatar: one pick per slot (a part id from assets/avatar/catalog.json, or null for none)
 * plus a background colour. Saved as JSON in a preference so it goes into backups, and packed into
 * friend-compare codes so friends see it.
 */
@Serializable
data class EggikuAvatar(
    val v: Int = 1,
    val body: String = "shell",
    val pattern: String? = null,
    val antenna: String? = null,
    val eyes: String? = "dots",
    val mouth: String? = "smile",
    val cheeks: String? = "blush",
    val hat: String? = null,
    val face: String? = null,
    val neck: String? = null,
    val ear: String? = null,
    val back: String? = null,
    val held: String? = null,
    val backEffect: String? = null,
    val frontEffect: String? = null,
    val bgColor: String = "#FFE3C2",
    val bgPattern: String? = "solid",
    /** Rank id to show as the border; null shows the current rank */
    val border: String? = null,
) {
    /** The pick for a catalog slot name ("body", "hat", "back_effect", ...) */
    operator fun get(slot: String): String? = when (slot) {
        "body" -> body
        "pattern" -> pattern
        "antenna" -> antenna
        "eyes" -> eyes
        "mouth" -> mouth
        "cheeks" -> cheeks
        "hat" -> hat
        "face" -> face
        "neck" -> neck
        "ear" -> ear
        "back" -> back
        "held" -> held
        "back_effect" -> backEffect
        "front_effect" -> frontEffect
        "background_pattern" -> bgPattern
        else -> null
    }

    fun with(slot: String, id: String?): EggikuAvatar = when (slot) {
        "body" -> copy(body = id ?: body)
        "pattern" -> copy(pattern = id)
        "antenna" -> copy(antenna = id)
        "eyes" -> copy(eyes = id)
        "mouth" -> copy(mouth = id)
        "cheeks" -> copy(cheeks = id)
        "hat" -> copy(hat = id)
        "face" -> copy(face = id)
        "neck" -> copy(neck = id)
        "ear" -> copy(ear = id)
        "back" -> copy(back = id)
        "held" -> copy(held = id)
        "back_effect" -> copy(backEffect = id)
        "front_effect" -> copy(frontEffect = id)
        "background_pattern" -> copy(bgPattern = id)
        else -> this
    }
}

/** What's in assets/avatar/catalog.json (only the fields the app uses) */
object EggikuAvatarCatalog {

    @Serializable
    data class Unlock(
        val type: String = "free",
        val ladder: String? = null,
        val tier: Int? = null,
        val key: String? = null,
        val rank: String? = null,
        val condition: String? = null,
        // KMK -->
        /** For "tree" and "prestige" unlocks: the rank tree branch key */
        val branch: String? = null,
        /** For "tree" unlocks: the rank (1..10) to reach in [branch] */
        val node: Int? = null,
        /** For "prestige" unlocks: prestige stars needed in [branch] */
        val level: Int? = null,
        // KMK <--
    )

    @Serializable
    data class Part(
        val id: String,
        val name: String,
        val slot: String,
        val layer: String,
        val files: JsonElement? = null,
        val unlock: Unlock = Unlock(),
        val collection: String? = null,
        val darkFace: Boolean = false,
    ) {
        val isTrope: Boolean get() = collection == "manga-webtoon-comic"
    }

    @Serializable
    data class FilePair(val svg: String, val png: String)

    @Serializable
    data class Rank(
        val id: String,
        val name: String,
        val xp: Int? = null,
        val secret: Boolean = false,
        val accent: String? = null,
        val files: FilePair? = null,
        // KMK -->
        val branch: String? = null,
        val node: Int? = null,
        // KMK <--
    )

    @Serializable
    data class Catalog(
        val layers: List<String> = emptyList(),
        val parts: List<Part> = emptyList(),
        val ranks: List<Rank> = emptyList(),
        val darkBodies: List<String> = emptyList(),
        val backgroundColors: List<String> = emptyList(),
    ) {
        private val byKey by lazy { parts.associateBy { it.slot + "/" + it.id } }
        fun part(slot: String, id: String?): Part? = id?.let { byKey["$slot/$it"] }
        fun slot(slot: String): List<Part> = parts.filter { it.slot == slot }
        fun rank(id: String?): Rank? = ranks.firstOrNull { it.id == id }
    }

    /** Slots in the order the editor shows them */
    val SLOTS = listOf(
        "body", "pattern", "antenna", "eyes", "mouth", "cheeks", "hat", "face", "neck", "ear",
        "back", "held", "front_effect", "back_effect", "background_pattern",
    )

    /** Slots that can be left empty */
    val OPTIONAL = SLOTS.toSet() - setOf("body", "background_pattern")

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Catalog? = null

    fun get(context: Context): Catalog {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: try {
                context.assets.open("avatar/catalog.json").bufferedReader().use { json.decodeFromString<Catalog>(it.readText()) }
            } catch (e: Exception) {
                xLogE("Eggiku avatar catalog failed to load", e)
                Catalog()
            }.also { cached = it }
        }
    }

    private fun JsonElement?.path(vararg keys: String, svg: Boolean): String? {
        var node: JsonElement? = this
        for (k in keys) node = (node as? JsonObject)?.get(k)
        return ((node as? JsonObject)?.get(if (svg) "svg" else "png") as? JsonPrimitive)?.contentOrNull
    }

    /**
     * The asset files (inside assets/avatar/) to draw for one part, back to front.
     * Bodies return base then shade; the pattern goes between them (see [layers]).
     */
    fun Part.filesFor(avatar: EggikuAvatar, darkFace: Boolean, svg: Boolean = true): List<String> = when (slot) {
        "body" -> listOfNotNull(files.path("base", svg = svg))
        "antenna" -> listOfNotNull(files.path("byBody", avatar.body, svg = svg) ?: files.path("byBody", "shell", svg = svg))
        "eyes", "mouth" -> listOfNotNull(files.path(if (darkFace) "light" else "default", svg = svg))
        else -> listOfNotNull(files.path(svg = svg))
    }

    /** A single picture of the part for the editor grid */
    fun Part.thumbnail(avatar: EggikuAvatar): String? = filesFor(avatar, darkFace = false, svg = false).firstOrNull()

    private fun Part.shade(svg: Boolean) = files.path("shade", svg = svg)

    /** Every asset file of an avatar in drawing order (background colour not included) */
    fun Catalog.drawOrder(avatar: EggikuAvatar, svg: Boolean = true): List<String> {
        val body = part("body", avatar.body) ?: part("body", "shell")
        val pattern = part("pattern", avatar.pattern)
        val dark = (body?.id in darkBodies) || pattern?.darkFace == true
        fun pick(slot: String) = part(slot, avatar[slot])?.filesFor(avatar, dark, svg).orEmpty()
        return buildList {
            addAll(pick("background_pattern"))
            addAll(pick("back_effect"))
            addAll(pick("back"))
            addAll(pick("antenna"))
            body?.let { addAll(it.filesFor(avatar, dark, svg)) }
            pattern?.let { addAll(it.filesFor(avatar, dark, svg)) }
            body?.shade(svg)?.let { add(it) }
            addAll(pick("cheeks"))
            addAll(pick("eyes"))
            addAll(pick("mouth"))
            addAll(pick("face"))
            addAll(pick("neck"))
            addAll(pick("ear"))
            addAll(pick("held"))
            addAll(pick("hat"))
            addAll(pick("front_effect"))
        }
    }
}

/** Your saved avatar and which unlocked parts you've already seen */
object EggikuAvatars {
    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    private fun pref() = preferenceStore.getString("eggiku_avatar_v1", "")
    fun seenPref() = preferenceStore.getStringSet("eggiku_avatar_seen_v1", emptySet())

    fun decode(raw: String): EggikuAvatar = if (raw.isBlank()) {
        EggikuAvatar()
    } else {
        try {
            json.decodeFromString<EggikuAvatar>(raw)
        } catch (_: Exception) {
            EggikuAvatar()
        }
    }

    fun mine(): EggikuAvatar = decode(pref().get())
    fun changes(): Flow<EggikuAvatar> = pref().changes().map(::decode)
    fun save(avatar: EggikuAvatar) = pref().set(json.encodeToString(avatar))

    // KMK -->
    const val OUTFIT_SLOTS = 3

    /** Saved outfits as a JSON list; empty slots are null */
    fun outfitsPref() = preferenceStore.getString("eggiku_avatar_outfits_v1", "")

    fun decodeOutfits(raw: String): List<EggikuAvatar?> = if (raw.isBlank()) {
        emptyList()
    } else {
        try {
            json.decodeFromString<List<EggikuAvatar?>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveOutfit(index: Int, avatar: EggikuAvatar) {
        val outfits = decodeOutfits(outfitsPref().get()).toMutableList()
        while (outfits.size < OUTFIT_SLOTS) outfits += null
        outfits[index.coerceIn(0, OUTFIT_SLOTS - 1)] = avatar
        outfitsPref().set(json.encodeToString(outfits.toList()))
    }
    // KMK <--
}

/**
 * What someone has earned, for avatar unlocks and rank XP. Built from your own earned
 * achievements or from a friend's compare code, so both work the same way.
 */
data class EggikuAvatarProgress(
    val tiers: Map<String, Int>,
    val badges: Set<String>,
    /** Their rank tree; null for compare codes from before the tree, then it's guessed from XP */
    val tree: EggikuRankTree.State? = null,
) {
    val xp: Int get() = tiers.values.sum() * XP_PER_TIER + badges.size * XP_PER_BADGE

    /** The secret Glib Glorb rank, earned with the liftoff badge */
    val glorb: Boolean get() = "liftoff" in badges

    val treeState: EggikuRankTree.State get() = tree ?: EggikuRankTree.guess(xp)

    val standing: EggikuRankTree.Standing get() = EggikuRankTree.standing(xp, treeState)

    fun rank(catalog: EggikuAvatarCatalog.Catalog): EggikuAvatarCatalog.Rank? {
        if (glorb) catalog.ranks.firstOrNull { it.secret }?.let { return it }
        val standing = standing
        val branch = standing.branch
        if (branch != null) {
            val node = if (standing.node > 0) standing.node else EggikuRankTree.NODES
            catalog.rank(EggikuRankTree.rankId(branch, node))?.let { return it }
        }
        return catalog.rank(START_RANK) ?: catalog.ranks.firstOrNull()
    }

    /** The next rank in the branch you're on, if there is one */
    fun nextRank(catalog: EggikuAvatarCatalog.Catalog): EggikuAvatarCatalog.Rank? {
        val standing = standing
        val branch = standing.branch ?: return null
        if (standing.node >= EggikuRankTree.NODES) return null
        return catalog.rank(EggikuRankTree.rankId(branch, standing.node + 1))
    }

    fun hasRank(catalog: EggikuAvatarCatalog.Catalog, id: String?): Boolean {
        if (id == null) return false
        if (id == START_RANK) return true
        val wanted = catalog.rank(id)
        if (wanted?.secret == true) return glorb
        LEGACY_RANK_XP[id]?.let { return it <= xp }
        val state = treeState
        val branchKey = wanted?.branch ?: id.substringBefore('_')
        val branch = EggikuRankTree.Branch.of(branchKey) ?: return false
        if (id == EggikuRankTree.gildedId(branch)) return state.prestige(branch) >= 1
        val node = wanted?.node ?: id.substringAfter('_').toIntOrNull() ?: return false
        return state.nodesReached(branch) >= node
    }

    fun isUnlocked(catalog: EggikuAvatarCatalog.Catalog, part: EggikuAvatarCatalog.Part): Boolean {
        val unlock = part.unlock
        return when (unlock.type) {
            "ladder" -> {
                val ladder = unlock.ladder
                if (ladder == null || EggikuAchievements.ladders.none { it.key == ladder }) {
                    xLogE("Eggiku avatar: unknown ladder $ladder for ${part.slot}/${part.id}")
                    true
                } else {
                    (tiers[ladder] ?: 0) > (unlock.tier ?: 0)
                }
            }
            "badge" -> {
                val key = unlock.key
                if (key == null || EggikuAchievements.oneOffs.none { it.key == key }) {
                    xLogE("Eggiku avatar: unknown badge $key for ${part.slot}/${part.id}")
                    true
                } else {
                    key in badges
                }
            }
            "rank" -> hasRank(catalog, unlock.rank)
            // KMK -->
            "tree" -> {
                val branch = EggikuRankTree.Branch.of(unlock.branch) ?: return true
                treeState.nodesReached(branch) >= (unlock.node ?: 1)
            }
            "prestige" -> {
                val branch = EggikuRankTree.Branch.of(unlock.branch) ?: return true
                treeState.prestige(branch) >= (unlock.level ?: 1)
            }
            // KMK <--
            else -> true
        }
    }

    /** The rank border to draw: the one picked (if still earned), otherwise the current rank */
    fun border(catalog: EggikuAvatarCatalog.Catalog, avatar: EggikuAvatar): EggikuAvatarCatalog.Rank? =
        avatar.border?.takeIf { hasRank(catalog, it) }?.let { catalog.rank(it) } ?: rank(catalog)

    companion object {
        const val XP_PER_TIER = 10
        const val XP_PER_BADGE = 25

        /** Where everyone starts, before their first rank step */
        const val START_RANK = "egg"

        /** XP the ranks from before the tree needed, so parts that asked for them still unlock */
        private val LEGACY_RANK_XP = mapOf(
            "raw" to 0,
            "cracked" to 20,
            "hatchling" to 60,
            "soft" to 150,
            "hard" to 300,
            "sunny" to 500,
            "golden" to 800,
            "cosmic" to 1200,
        )

        /** From your own earned achievements (these never go backwards) */
        fun mine(): EggikuAvatarProgress {
            val keys = EggikuAchievements.earned().keys
            val tiers = HashMap<String, Int>()
            val badges = HashSet<String>()
            keys.forEach { key ->
                when {
                    key.startsWith("L:") -> {
                        val ladder = key.substringAfter("L:").substringBeforeLast(':')
                        val index = key.substringAfterLast(':').toIntOrNull() ?: return@forEach
                        tiers[ladder] = maxOf(tiers[ladder] ?: 0, index + 1)
                    }
                    key.startsWith("O:") -> badges += key.removePrefix("O:")
                }
            }
            val xp = tiers.values.sum() * XP_PER_TIER + badges.size * XP_PER_BADGE
            return EggikuAvatarProgress(tiers, badges, EggikuRankTree.sync(xp))
        }

        fun from(profile: EggikuCompare.Profile) =
            EggikuAvatarProgress(profile.tiers, profile.badges.toSet(), profile.tree)
    }
}
// KMK <--
