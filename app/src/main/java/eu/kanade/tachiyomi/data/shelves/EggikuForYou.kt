package eu.kanade.tachiyomi.data.shelves

import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import eu.kanade.tachiyomi.data.duplicates.EggikuDuplicates
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku "More like this": picks from your own unread pile that share the most tags with what you
 * rated highly, nudged by why you dropped things (drop for slow pacing and long series sink; drop
 * for the story or art and those series' tags count against), plus what your friends rated best.
 */
object EggikuForYou {

    data class Pick(
        val manga: LibraryManga,
        val score: Double,
        /** Tags shared with the series it's like */
        val shared: List<String>,
    )

    data class Because(
        val liked: LibraryManga,
        val stars: Int,
        val picks: List<Pick>,
    )

    data class FriendPick(
        val friend: String,
        val title: String,
        /** Set when the series is already in your library */
        val mangaId: Long?,
    )

    data class Result(
        val because: List<Because>,
        val pile: List<Pick>,
        val friends: List<FriendPick>,
    )

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Your best-rated titles, kept so friend cards can carry them */
    fun myTopTitlesPref() = preferenceStore.getStringSet("eggiku_my_top_titles", emptySet())

    /** "friend|title", from the last friend cards you opened */
    private fun friendPicksPref() = preferenceStore.getStringSet("eggiku_friend_picks", emptySet())

    fun myTopTitles(): List<String> = myTopTitlesPref().get().sorted()

    /** Remembers a friend's best-rated series from their card */
    fun rememberFriend(profile: EggikuCompare.Profile) {
        val name = profile.name.ifBlank { "?" }.replace("|", "")
        if (profile.picks.isEmpty()) return
        val others = friendPicksPref().get().filterNot { it.substringBefore('|') == name }
        friendPicksPref().set((others + profile.picks.take(MAX_SHARED).map { "$name|$it" }).toSet())
    }

    /** Updates the titles your own friend card shares */
    suspend fun refreshMyTopTitles() = withIOContext {
        val getManga = Injekt.get<GetManga>()
        val titles = EggikuRatings.all()
            .filterValues { it >= 4 }
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, _) -> getManga.await(id)?.title }
            .take(MAX_SHARED)
        myTopTitlesPref().set(titles.toSet())
    }

    private fun tags(manga: LibraryManga): Set<String> =
        manga.manga.genre.orEmpty().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    suspend fun build(): Result = withIOContext {
        val library = Injekt.get<GetLibraryManga>().await().distinctBy { it.id }
        val byId = library.associateBy { it.id }
        val ratings = EggikuRatings.all()
        val shelves = EggikuShelves.store()

        // Tags to steer away from: the tags of series dropped for their story or art, and for
        // "lost interest" a lighter touch
        val avoid = mutableMapOf<String, Double>()
        var avoidLong = false
        shelves.dropped.forEach { (id, drop) ->
            val manga = byId[id] ?: return@forEach
            val weight = when (drop.reasonEnum) {
                EggikuShelves.Reason.STORY, EggikuShelves.Reason.ART -> 1.0
                EggikuShelves.Reason.LOST_INTEREST -> 0.5
                EggikuShelves.Reason.SLOW, EggikuShelves.Reason.TOO_LONG -> {
                    avoidLong = true
                    0.25
                }
                EggikuShelves.Reason.OTHER -> 0.25
            }
            tags(manga).forEach { avoid[it] = (avoid[it] ?: 0.0) + weight }
        }

        // Your unread pile: in the library, never started, not dropped
        val pile = library.filter { !it.hasStarted && it.id !in shelves.dropped }

        fun score(candidate: LibraryManga, likedTags: Set<String>): Pick? {
            val candidateTags = tags(candidate)
            val shared = candidateTags.intersect(likedTags)
            if (shared.isEmpty()) return null
            val overlap = shared.size.toDouble() / (candidateTags.size + likedTags.size - shared.size).coerceAtLeast(1)
            val penalty = candidateTags.sumOf { avoid[it] ?: 0.0 } * 0.04
            val longPenalty = if (avoidLong && candidate.totalChapters > LONG_SERIES) 0.15 else 0.0
            return Pick(candidate, overlap - penalty - longPenalty, shared.sorted())
        }

        val liked = ratings.filterValues { it >= 4 }
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, stars) -> byId[id]?.let { it to stars } }
            .take(MAX_BECAUSE)
        val because = liked.mapNotNull { (manga, stars) ->
            val likedTags = tags(manga)
            val picks = pile.filter { it.id != manga.id }
                .mapNotNull { score(it, likedTags) }
                .filter { it.score > 0.05 }
                .sortedByDescending { it.score }
                .take(PICKS_PER_ROW)
            Because(manga, stars, picks)
        }

        // Everything you rated 4+, together, against the whole pile
        val allLikedTags = liked.flatMap { tags(it.first) }.toSet()
        val pileRanked = if (allLikedTags.isEmpty()) {
            pile.sortedByDescending { it.manga.dateAdded }.take(PILE_SIZE).map { Pick(it, 0.0, emptyList()) }
        } else {
            pile.mapNotNull { score(it, allLikedTags) }.sortedByDescending { it.score }.take(PILE_SIZE)
        }

        val titleIndex = library.associateBy { EggikuDuplicates.normalize(it.manga.title) }
        val friends = friendPicksPref().get().map { entry ->
            val title = entry.substringAfter('|')
            FriendPick(
                friend = entry.substringBefore('|'),
                title = title,
                mangaId = titleIndex[EggikuDuplicates.normalize(title)]?.id,
            )
        }.sortedWith(compareBy({ it.friend.lowercase() }, { it.title.lowercase() }))

        Result(because, pileRanked, friends)
    }

    private const val MAX_SHARED = 12
    private const val MAX_BECAUSE = 6
    private const val PICKS_PER_ROW = 8
    private const val PILE_SIZE = 18
    private const val LONG_SERIES = 150L
}
// KMK <--
