package mihon.feature.migration.list.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.feature.migration.list.MigrationListScreenModel.ChapterInfo
import tachiyomi.domain.manga.model.Manga
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    val manga: Manga,
    val chapterCount: Int,
    val latestChapter: Double?,
    val source: String,
    parentContext: CoroutineContext,
) {
    val migrationScope = CoroutineScope(parentContext + SupervisorJob() + Dispatchers.Default)

    // KMK -->
    var searchingJob: Deferred<Pair<Manga, ChapterInfo>?>? = null
    // KMK <--

    val searchResult = MutableStateFlow<SearchResult>(SearchResult.Searching)

    // KMK -->
    /** Every source that had a match, best first, to compare side by side */
    val eggikuCandidates = MutableStateFlow<List<EggikuCandidate>>(emptyList())

    /** The picked match might be the wrong series; it waits for a tap before it moves */
    val eggikuUnsure = MutableStateFlow(false)
    val eggikuConfirmed = MutableStateFlow(false)

    data class EggikuCandidate(
        val manga: Manga,
        val source: String,
        val chapterCount: Int,
        val latestChapter: Double?,
        val lastUploadMillis: Long,
        val carriedRead: Int,
        val totalRead: Int,
    )
    // KMK <--

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult
        data class Success(
            val manga: Manga,
            val chapterCount: Int,
            val latestChapter: Double?,
            val source: String,
        ) : SearchResult
    }
}
