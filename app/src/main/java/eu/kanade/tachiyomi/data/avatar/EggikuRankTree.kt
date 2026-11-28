package eu.kanade.tachiyomi.data.avatar

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku rank tree. XP (from achievement tiers and badges) turns into egg points, one every
 * [XP_PER_POINT]. Points go into the branch you picked, one rank step at a time: each branch has
 * [NODES] ranks with [STEPS] steps (I, II, III) each. You stay on a branch until it's finished;
 * then you can rebirth it for a prestige star (and its gilded rewards) or start another branch.
 */
object EggikuRankTree {

    const val XP_PER_POINT = 40
    const val NODES = 10
    const val STEPS = 3
    const val STEPS_PER_BRANCH = NODES * STEPS
    const val MAX_PRESTIGE = 3

    enum class Branch(val key: String, val title: StringResource, val emoji: String, val accent: Long) {
        CHICKEN("chicken", KMR.strings.tree_branch_chicken, "🐔", 0xFFF2B33D),
        READER("reader", KMR.strings.tree_branch_reader, "📖", 0xFF7FA8E8),
        COSMIC("cosmic", KMR.strings.tree_branch_cosmic, "🪐", 0xFF9C7BFF),
        DEEP_SEA("deepsea", KMR.strings.tree_branch_deepsea, "🐙", 0xFF2FB5C9),
        GARDEN("garden", KMR.strings.tree_branch_garden, "🌱", 0xFF6DBE5A),
        SAMURAI("samurai", KMR.strings.tree_branch_samurai, "⚔️", 0xFFD8453B),
        ;

        companion object {
            fun of(key: String?): Branch? = entries.firstOrNull { it.key == key }
        }
    }

    /** Rank names for each branch, lowest to highest */
    val nodeNames: Map<Branch, List<StringResource>> = mapOf(
        Branch.CHICKEN to listOf(
            KMR.strings.tree_chicken_1, KMR.strings.tree_chicken_2, KMR.strings.tree_chicken_3,
            KMR.strings.tree_chicken_4, KMR.strings.tree_chicken_5, KMR.strings.tree_chicken_6,
            KMR.strings.tree_chicken_7, KMR.strings.tree_chicken_8, KMR.strings.tree_chicken_9,
            KMR.strings.tree_chicken_10,
        ),
        Branch.READER to listOf(
            KMR.strings.tree_reader_1, KMR.strings.tree_reader_2, KMR.strings.tree_reader_3,
            KMR.strings.tree_reader_4, KMR.strings.tree_reader_5, KMR.strings.tree_reader_6,
            KMR.strings.tree_reader_7, KMR.strings.tree_reader_8, KMR.strings.tree_reader_9,
            KMR.strings.tree_reader_10,
        ),
        Branch.COSMIC to listOf(
            KMR.strings.tree_cosmic_1, KMR.strings.tree_cosmic_2, KMR.strings.tree_cosmic_3,
            KMR.strings.tree_cosmic_4, KMR.strings.tree_cosmic_5, KMR.strings.tree_cosmic_6,
            KMR.strings.tree_cosmic_7, KMR.strings.tree_cosmic_8, KMR.strings.tree_cosmic_9,
            KMR.strings.tree_cosmic_10,
        ),
        Branch.DEEP_SEA to listOf(
            KMR.strings.tree_deepsea_1, KMR.strings.tree_deepsea_2, KMR.strings.tree_deepsea_3,
            KMR.strings.tree_deepsea_4, KMR.strings.tree_deepsea_5, KMR.strings.tree_deepsea_6,
            KMR.strings.tree_deepsea_7, KMR.strings.tree_deepsea_8, KMR.strings.tree_deepsea_9,
            KMR.strings.tree_deepsea_10,
        ),
        Branch.GARDEN to listOf(
            KMR.strings.tree_garden_1, KMR.strings.tree_garden_2, KMR.strings.tree_garden_3,
            KMR.strings.tree_garden_4, KMR.strings.tree_garden_5, KMR.strings.tree_garden_6,
            KMR.strings.tree_garden_7, KMR.strings.tree_garden_8, KMR.strings.tree_garden_9,
            KMR.strings.tree_garden_10,
        ),
        Branch.SAMURAI to listOf(
            KMR.strings.tree_samurai_1, KMR.strings.tree_samurai_2, KMR.strings.tree_samurai_3,
            KMR.strings.tree_samurai_4, KMR.strings.tree_samurai_5, KMR.strings.tree_samurai_6,
            KMR.strings.tree_samurai_7, KMR.strings.tree_samurai_8, KMR.strings.tree_samurai_9,
            KMR.strings.tree_samurai_10,
        ),
    )

    /** Saved tree progress. Goes into backups (it's a preference) and friend compare codes */
    @Serializable
    data class State(
        val v: Int = 1,
        /** Branch key you're working on, or null before you've picked one */
        val active: String? = null,
        /** Rank steps bought per branch key, 0..[STEPS_PER_BRANCH] */
        val steps: Map<String, Int> = emptyMap(),
        /** Prestige stars per branch key */
        val prestige: Map<String, Int> = emptyMap(),
        /** Egg points spent so far, rebirths included */
        val spent: Int = 0,
    ) {
        fun steps(branch: Branch): Int = (steps[branch.key] ?: 0).coerceIn(0, STEPS_PER_BRANCH)
        fun prestige(branch: Branch): Int = (prestige[branch.key] ?: 0).coerceIn(0, MAX_PRESTIGE)
        val activeBranch: Branch? get() = Branch.of(active)
        val totalPrestige: Int get() = Branch.entries.sumOf { prestige(it) }

        /** Ranks reached in a branch: 0 before the first step, up to [NODES] */
        fun nodesReached(branch: Branch): Int =
            if (prestige(branch) > 0) NODES else (steps(branch) + STEPS - 1) / STEPS

        fun isFinished(branch: Branch) = steps(branch) >= STEPS_PER_BRANCH

        /** You can switch when the current branch is finished or hasn't been started */
        fun canSwitch(): Boolean = activeBranch?.let { isFinished(it) || steps(it) == 0 } ?: true

        /** The branch to name your rank after: the active one, or the one you've gone furthest in */
        fun shownBranch(): Branch? = activeBranch?.takeIf { steps(it) > 0 || prestige(it) > 0 }
            ?: Branch.entries.maxByOrNull { prestige(it) * 100 + steps(it) }?.takeIf { steps(it) > 0 || prestige(it) > 0 }
    }

    /** Where you stand, worked out from XP and the saved tree */
    data class Standing(
        val state: State,
        val xp: Int,
        val points: Int,
    ) {
        val unspent: Int get() = (points - state.spent).coerceAtLeast(0)
        val branch: Branch? get() = state.shownBranch()

        /** 1-based rank (node) index in [branch], 0 if none yet */
        val node: Int get() = branch?.let { b -> ((state.steps(b) + STEPS - 1) / STEPS).coerceAtLeast(0) } ?: 0

        /** 1..3 for I..III within the current node */
        val step: Int get() = branch?.let { b -> ((state.steps(b) - 1) % STEPS) + 1 } ?: 0

        val prestige: Int get() = branch?.let { state.prestige(it) } ?: 0

        /** XP into the next egg point, for progress bars */
        val xpIntoPoint: Int get() = xp % XP_PER_POINT
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val lock = Any()

    private fun pref() = preferenceStore.getString("eggiku_rank_tree_v1", "")

    fun decode(raw: String): State = if (raw.isBlank()) {
        State()
    } else {
        try {
            json.decodeFromString<State>(raw)
        } catch (_: Exception) {
            State()
        }
    }

    fun state(): State = decode(pref().get())
    fun changes(): Flow<State> = pref().changes().map(::decode)
    private fun save(state: State) = pref().set(json.encodeToString(state))

    fun pointsFor(xp: Int): Int = xp / XP_PER_POINT

    fun standing(xp: Int, state: State = state()): Standing = Standing(state, xp, pointsFor(xp))

    /** Puts any unspent points into the active branch. Returns the new state */
    fun sync(xp: Int): State = synchronized(lock) {
        val state = state()
        val synced = spend(state, pointsFor(xp))
        if (synced != state) save(synced)
        synced
    }

    /** Pure version of [sync], also used for friends' trees */
    fun spend(state: State, points: Int): State {
        val branch = state.activeBranch ?: return state
        val have = state.steps(branch)
        val room = STEPS_PER_BRANCH - have
        val buy = minOf(room, (points - state.spent).coerceAtLeast(0))
        if (buy <= 0) return state
        return state.copy(
            steps = state.steps + (branch.key to have + buy),
            spent = state.spent + buy,
        )
    }

    fun choose(branch: Branch, xp: Int): Boolean = synchronized(lock) {
        val state = state()
        if (!state.canSwitch() && state.activeBranch != branch) return false
        save(spend(state.copy(active = branch.key), pointsFor(xp)))
        true
    }

    /** Starts a finished branch over for a prestige star. Rewards you've unlocked stay yours */
    fun rebirth(branch: Branch, xp: Int): Boolean = synchronized(lock) {
        val state = state()
        if (!state.isFinished(branch) || state.prestige(branch) >= MAX_PRESTIGE) return false
        val reborn = state.copy(
            active = branch.key,
            steps = state.steps + (branch.key to 0),
            prestige = state.prestige + (branch.key to state.prestige(branch) + 1),
        )
        save(spend(reborn, pointsFor(xp)))
        true
    }

    /**
     * A tree for someone whose compare code came from an older Eggiku without one: their XP all
     * goes into the Chicken branch, so they still get a sensible rank.
     */
    fun guess(xp: Int): State = spend(State(active = Branch.CHICKEN.key), pointsFor(xp))

    /** Catalog rank id for a branch rank (also the border id), e.g. "cosmic_4" */
    fun rankId(branch: Branch, node: Int) = "${branch.key}_$node"

    /** Catalog rank id of a branch's gilded border, earned with its first prestige star */
    fun gildedId(branch: Branch) = "${branch.key}_gilded"
}
// KMK <--
