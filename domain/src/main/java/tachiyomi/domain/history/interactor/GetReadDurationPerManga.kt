package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.HistoryRepository

// KMK -->
/** Eggiku: how long has been spent reading each series, in milliseconds */
class GetReadDurationPerManga(
    private val repository: HistoryRepository,
) {

    suspend fun await(): Map<Long, Long> {
        return repository.getReadDurationPerManga()
    }
}
// KMK <--
