package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.data.search.EggikuAltTitles
import eu.kanade.tachiyomi.data.search.EggikuSearchGroups
import eu.kanade.tachiyomi.data.search.EggikuSearchHistory
import eu.kanade.tachiyomi.data.search.EggikuSearchPrefs
import eu.kanade.tachiyomi.data.search.EggikuSearchText
import eu.kanade.tachiyomi.data.search.EggikuSourceHealth
import eu.kanade.tachiyomi.extension.EggikuNsfwOverride
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class SearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<SearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledSources().get()
    protected val pinnedSources = sourcePreferences.pinnedSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    // KMK -->
    /** Order sources finished in this search, so the fastest answers show first */
    private val eggikuFinishOrder = ConcurrentHashMap<Long, Int>()
    private val eggikuFinishCounter = AtomicInteger(0)
    private var eggikuMineJob: Job? = null
    // KMK <--

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            // KMK -->
            { eggikuFinishOrder[it.id] ?: Int.MAX_VALUE },
            // KMK <--
            { "${it.name.lowercase()} (${it.lang})" },
            // KMK -->
            // toSortedMap() drops keys that compare equal, so sources sharing a name and language need a unique tie-breaker
            { it.id },
            // KMK <--
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
        // KMK -->
        screenModelScope.launch {
            preferences.globalSearchPinnedState().changes().collectLatest { state ->
                mutableState.update { it.copy(sourceFilter = state) }
            }
        }
        val savedGroup = EggikuSearchGroups.selected().get().ifBlank { null }
            ?.takeIf { id -> id == EggikuSearchGroups.FAST || EggikuSearchGroups.all().any { it.id == id } }
        mutableState.update {
            it.copy(
                eggiku = it.eggiku.copy(
                    groupId = savedGroup,
                    merged = EggikuSearchPrefs.merged().get(),
                    altTitlesOn = EggikuSearchPrefs.altTitles().get(),
                    includeMine = EggikuSearchPrefs.includeMine().get(),
                ),
            )
        }
        // KMK <--
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<Source> = eggikuBaseEnabledSources()

    // KMK -->
    /** Enabled sources before the Pinned / All choice, which search groups replace */
    protected fun eggikuBaseEnabledSources(): List<Source> {
        // KMK -->
        val hideNsfw = preferences.eggikuHideNsfw().get()
        val nsfwIds = if (hideNsfw) {
            extensionManager.installedExtensionsFlow.value
                .filter { EggikuNsfwOverride.isNsfw(it) }
                .flatMap { ext -> ext.sources.map { it.id } }
                .toSet()
        } else {
            emptySet()
        }
        // KMK <--
        return sourceManager.getVisibleSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            // KMK -->
            .filter { it.id !in nsfwIds }
            // KMK <--
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
    // KMK <--

    // KMK -->
    fun hasPinnedSources(): Boolean = getEnabledSources().any { "${it.id}" in pinnedSources }

    fun shouldPinnedSourcesHidden() {
        if (!hasPinnedSources()) {
            preferences.globalSearchPinnedState().set(SourceFilter.All)
        }
    }
    // KMK <--

    private fun getSelectedSources(): List<Source> {
        // KMK -->
        val groupId = state.value.eggiku.groupId
        val enabledSources = when {
            groupId == null -> getEnabledSources()
            groupId == EggikuSearchGroups.FAST -> eggikuBaseEnabledSources().filter { EggikuSourceHealth.isFast(it.id) }
            else -> {
                val ids = EggikuSearchGroups.all().firstOrNull { it.id == groupId }?.sourceIds?.toSet().orEmpty()
                sourceManager.getVisibleSources().filter { it.id in ids }
            }
        }
        // KMK <--

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        // SY -->
        val filteredSourceIds = extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .map { it.id }
        return enabledSources.filter { it.id in filteredSourceIds }
        // SY <--
    }

    // KMK -->
    /** Sources to search now, minus the ones that keep failing (unless that's switched off) */
    private fun eggikuSourcesToSearch(): List<Source> {
        val selected = getSelectedSources()
        if (!EggikuSearchPrefs.skipFailing().get() || !extensionFilter.isNullOrEmpty()) {
            mutableState.update { it.copy(eggiku = it.eggiku.copy(skipped = emptyList())) }
            return selected
        }
        val (skipped, searched) = selected.partition { EggikuSourceHealth.shouldSkip(it.id) }
        mutableState.update { it.copy(eggiku = it.eggiku.copy(skipped = skipped)) }
        return searched
    }

    /** One source's search, with the time limit, tag search and other names worked in */
    private suspend fun eggikuFetch(source: Source, query: String, tag: String?): List<SManga> {
        val seconds = EggikuSearchPrefs.timeoutSeconds().get()
        val block: suspend () -> List<SManga> = {
            if (tag != null) {
                val filters = source.getFilterList()
                if (!EggikuSearchText.applyTag(filters, tag)) throw EggikuNoTagException()
                source.getSearchManga(1, "", filters).mangas
            } else {
                source.getSearchManga(1, query.sanitize(), source.getFilterList()).mangas
            }
        }
        if (seconds <= 0) return block()
        return try {
            withTimeout(seconds * 1000L) { block() }
        } catch (_: TimeoutCancellationException) {
            throw EggikuTooSlowException(seconds)
        }
    }

    /** Re-runs the search after an Eggiku option changed */
    private fun eggikuResearch() {
        lastQuery = null
        search()
    }

    fun eggikuSelectGroup(groupId: String?) {
        EggikuSearchGroups.selected().set(groupId.orEmpty())
        mutableState.update { it.copy(eggiku = it.eggiku.copy(groupId = groupId)) }
        eggikuResearch()
    }

    /** Pinned / All, which also leaves any search group */
    fun eggikuChooseSourceFilter(filter: SourceFilter) {
        EggikuSearchGroups.selected().set("")
        preferences.globalSearchPinnedState().set(filter)
        mutableState.update { it.copy(sourceFilter = filter, eggiku = it.eggiku.copy(groupId = null)) }
        eggikuResearch()
    }

    fun eggikuToggleTagMode() {
        mutableState.update { it.copy(eggiku = it.eggiku.copy(tagMode = !it.eggiku.tagMode)) }
        eggikuResearch()
    }

    fun eggikuToggleMerged() {
        val on = !state.value.eggiku.merged
        EggikuSearchPrefs.merged().set(on)
        mutableState.update { it.copy(eggiku = it.eggiku.copy(merged = on)) }
    }

    fun eggikuToggleAltTitles() {
        val on = !state.value.eggiku.altTitlesOn
        EggikuSearchPrefs.altTitles().set(on)
        mutableState.update { it.copy(eggiku = it.eggiku.copy(altTitlesOn = on, altTitles = emptyList())) }
        eggikuResearch()
    }

    fun eggikuToggleMine() {
        val on = !state.value.eggiku.includeMine
        EggikuSearchPrefs.includeMine().set(on)
        mutableState.update { it.copy(eggiku = it.eggiku.copy(includeMine = on, mine = null)) }
        if (on) state.value.searchQuery?.let { eggikuSearchMine(it) }
    }

    fun eggikuSetFilters(filters: EggikuSearchFilters) {
        mutableState.update { it.copy(eggiku = it.eggiku.copy(filters = filters)) }
        if (filters.minChapters > 0) eggikuLoadKnownChapterCounts()
    }

    /** Searches the sources that were skipped for failing, and gives them another chance */
    fun eggikuSearchSkipped() {
        EggikuSourceHealth.forgive(state.value.eggiku.skipped.map { it.id })
        eggikuResearch()
    }

    /** Fetches chapter counts, newest chapter and status for these series, to compare sources */
    fun eggikuCompare(entries: List<Pair<Source, Manga>>) {
        val todo = entries.filter { (_, manga) -> manga.id !in state.value.eggiku.details && manga.id !in state.value.eggiku.comparing }
        if (todo.isEmpty()) return
        mutableState.update { it.copy(eggiku = it.eggiku.copy(comparing = it.eggiku.comparing + todo.map { e -> e.second.id })) }
        ioCoroutineScope.launch {
            todo.map { (source, manga) ->
                async(coroutineDispatcher) {
                    val details = try {
                        withTimeout(30_000L) {
                            val update = source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = true, fetchChapters = true)
                            EggikuSeriesDetails(
                                chapters = update.chapters.size,
                                latestUpload = update.chapters.maxOfOrNull { it.date_upload } ?: 0L,
                                status = update.manga.status.toLong(),
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                        EggikuSeriesDetails(0, 0L, 0L, failed = true)
                    }
                    mutableState.update {
                        it.copy(
                            eggiku = it.eggiku.copy(
                                details = it.eggiku.details + (manga.id to details),
                                comparing = it.eggiku.comparing - manga.id,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /** Chapter counts for results you've opened before, so the chapter filter has something to go on */
    private fun eggikuLoadKnownChapterCounts() {
        ioCoroutineScope.launch {
            val getChapters = Injekt.get<GetChaptersByMangaId>()
            val known = state.value.eggiku.details
            val results = state.value.items.values
                .filterIsInstance<SearchItemResult.Success>()
                .flatMap { it.result }
                .filter { it.initialized && it.id !in known }
                .distinctBy { it.id }
                .take(300)
            val counts = results.mapNotNull { manga ->
                val chapters = try {
                    getChapters.await(manga.id)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                if (chapters.isEmpty()) return@mapNotNull null
                manga.id to EggikuSeriesDetails(
                    chapters = chapters.size,
                    latestUpload = chapters.maxOf { it.dateUpload },
                    status = manga.status,
                )
            }.toMap()
            if (counts.isNotEmpty()) {
                mutableState.update { it.copy(eggiku = it.eggiku.copy(details = counts + it.eggiku.details)) }
            }
        }
    }

    /** Matches in your library and history */
    private fun eggikuSearchMine(query: String) {
        eggikuMineJob?.cancel()
        if (!state.value.eggiku.includeMine || state.value.eggiku.tagMode) {
            mutableState.update { it.copy(eggiku = it.eggiku.copy(mine = null)) }
            return
        }
        eggikuMineJob = ioCoroutineScope.launch {
            val key = EggikuSearchText.titleKey(query)
            fun matches(title: String): Boolean =
                title.contains(query.trim(), ignoreCase = true) || (key.isNotEmpty() && key in EggikuSearchText.titleKey(title))
            val library = try {
                Injekt.get<GetFavorites>().await().filter { matches(it.title) }.take(30)
            } catch (_: Exception) {
                emptyList()
            }
            val libraryIds = library.map { it.id }.toSet()
            val history = try {
                Injekt.get<GetHistory>().subscribe(query.trim(), null, null, null).first()
                    .distinctBy { it.mangaId }
                    .filter { it.mangaId !in libraryIds }
                    .take(30)
                    .mapNotNull { getManga.await(it.mangaId) }
            } catch (_: Exception) {
                emptyList()
            }
            if (isActive) {
                mutableState.update { it.copy(eggiku = it.eggiku.copy(mine = EggikuMine(library, history))) }
            }
        }
    }
    // KMK <--

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        preferences.globalSearchPinnedState().set(filter)
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return
        // KMK -->
        EggikuSearchHistory.record(query)
        // KMK <--

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        // KMK -->
        val eggiku = state.value.eggiku
        val tag = if (eggiku.tagMode) query.trim().removePrefix("#").trim() else null
        val sources = eggikuSourcesToSearch()
        eggikuFinishOrder.clear()
        eggikuFinishCounter.set(0)
        mutableState.update { it.copy(eggiku = it.eggiku.copy(timings = emptyMap(), altTitles = emptyList())) }
        eggikuSearchMine(query)
        // KMK <--

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            // KMK -->
            // Other names for the series, looked up while the sources are already searching
            val altTitles = if (eggiku.altTitlesOn && tag == null) {
                async {
                    EggikuAltTitles.find(query).also { titles ->
                        mutableState.update { it.copy(eggiku = it.eggiku.copy(altTitles = titles)) }
                    }
                }
            } else {
                null
            }
            // KMK <--
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    // KMK -->
                    val started = System.currentTimeMillis()
                    // KMK <--
                    try {
                        val page = withContext(coroutineDispatcher) {
                            // KMK -->
                            eggikuFetch(source, query, tag)
                            // KMK <--
                        }

                        // KMK -->
                        val took = System.currentTimeMillis() - started
                        EggikuSourceHealth.record(source.id, took, ok = true)
                        eggikuFinishOrder[source.id] = eggikuFinishCounter.incrementAndGet()
                        mutableState.update { it.copy(eggiku = it.eggiku.copy(timings = it.eggiku.timings + (source.id to took))) }
                        // KMK <--
                        val titles = page
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }

                        // KMK -->
                        // Then the other names, added to the same row
                        val extra = altTitles?.await().orEmpty()
                        if (extra.isNotEmpty() && isActive) {
                            val more = extra.flatMap { alt ->
                                try {
                                    withContext(coroutineDispatcher) { eggikuFetch(source, alt, null) }
                                } catch (e: Exception) {
                                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                                    emptyList()
                                }
                            }
                            if (more.isNotEmpty()) {
                                val combined = (titles + networkToLocalManga(more.map { it.toDomainManga(source.id) }))
                                    .distinctBy { it.url }
                                if (isActive) updateItem(source, SearchItemResult.Success(combined))
                            }
                        }
                        // KMK <--
                    } catch (e: Exception) {
                        // KMK -->
                        if (e is CancellationException && e !is EggikuTooSlowException) throw e
                        if (e !is EggikuNoTagException) {
                            EggikuSourceHealth.record(source.id, System.currentTimeMillis() - started, ok = false)
                        }
                        // KMK <--
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
            // KMK -->
            EggikuSourceHealth.save()
            // KMK <--
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        mutableState.update { currentState ->
            val newItems = currentState.items + (source to result)
            currentState.copy(
                items = newItems
                    .toSortedMap(sortComparator(newItems))
                    .toPersistentMap(),
            )
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        screenModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<Source, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
        // KMK -->
        val eggiku: EggikuSearchState = EggikuSearchState(),
        // KMK <--
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
            .toImmutableMap()

        // KMK -->
        /** [filteredItems] after Eggiku's filters (language, library, status, chapters, tags) */
        val eggikuItems = EggikuSearchFiltering.apply(items, eggiku)
            .filter { (_, result) -> result.isVisible(onlyShowHasResults) }
            .toImmutableMap()
        // KMK <--
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
