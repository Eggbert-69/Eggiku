package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.drop.EggikuDrops
import eu.kanade.tachiyomi.data.search.EggikuSearchGroups
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.EggikuMergedSeries
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.EggikuSearchFiltering
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.EggikuSearchFilters
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.EggikuSearchState
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.EggikuSeriesDetails
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.util.Locale

// KMK -->
/** What the Eggiku parts of global search can ask the screen to do */
class EggikuSearchActions(
    val onSelectGroup: (String?) -> Unit,
    val onManageGroups: () -> Unit,
    val onToggleTag: () -> Unit,
    val onToggleMerged: () -> Unit,
    val onToggleAltTitles: () -> Unit,
    val onToggleMine: () -> Unit,
    val onSetFilters: (EggikuSearchFilters) -> Unit,
    val onSearchSkipped: () -> Unit,
    val onCompare: (List<Pair<Source, Manga>>) -> Unit,
    val onFindByPicture: () -> Unit,
    val onOpenDrop: (EggikuDrops.Drop) -> Unit,
)

private fun seconds(ms: Long) = String.format(Locale.getDefault(), "%.1f s", ms / 1000f)

fun eggikuSourceSubtitle(source: Source, eggiku: EggikuSearchState): String {
    val lang = LocaleHelper.getLocalizedDisplayName(source.lang)
    val took = eggiku.timings[source.id] ?: return lang
    return "$lang · ${seconds(took)}"
}

/** Chips after "Has results": groups, tag search, merged, other names, your stuff, filters */
@Composable
fun RowScope.EggikuSearchChips(
    eggiku: EggikuSearchState,
    languages: List<String>,
    actions: EggikuSearchActions,
) {
    val groupsRaw by EggikuSearchGroups.pref().collectAsState()
    val groups = remember(groupsRaw) { EggikuSearchGroups.parse(groupsRaw) }
    var showFilters by remember { mutableStateOf(false) }

    VerticalDivider()
    Chip(
        selected = eggiku.groupId == EggikuSearchGroups.FAST,
        icon = Icons.Outlined.Bolt,
        label = stringResource(KMR.strings.search_group_fast),
        onClick = { actions.onSelectGroup(if (eggiku.groupId == EggikuSearchGroups.FAST) null else EggikuSearchGroups.FAST) },
    )
    groups.forEach { group ->
        Chip(
            selected = eggiku.groupId == group.id,
            icon = Icons.Outlined.FolderSpecial,
            label = group.name,
            onClick = { actions.onSelectGroup(if (eggiku.groupId == group.id) null else group.id) },
        )
    }
    Chip(
        selected = false,
        icon = Icons.Outlined.Add,
        label = stringResource(KMR.strings.search_groups),
        onClick = actions.onManageGroups,
    )
    VerticalDivider()
    Chip(
        selected = eggiku.filters.active,
        icon = Icons.Outlined.Tune,
        label = if (eggiku.filters.active) {
            stringResource(KMR.strings.search_filters_count, eggiku.filters.count)
        } else {
            stringResource(KMR.strings.search_filters)
        },
        onClick = { showFilters = true },
    )
    Chip(
        selected = eggiku.merged,
        icon = Icons.Outlined.Layers,
        label = stringResource(KMR.strings.search_merged),
        onClick = actions.onToggleMerged,
    )
    Chip(
        selected = eggiku.tagMode,
        icon = null,
        label = "# " + stringResource(KMR.strings.search_tag_mode),
        onClick = actions.onToggleTag,
    )
    Chip(
        selected = eggiku.altTitlesOn,
        icon = Icons.Outlined.Translate,
        label = stringResource(KMR.strings.search_alt_titles),
        onClick = actions.onToggleAltTitles,
    )
    Chip(
        selected = eggiku.includeMine,
        icon = Icons.Outlined.Person,
        label = stringResource(KMR.strings.search_mine),
        onClick = actions.onToggleMine,
    )

    if (showFilters) {
        EggikuSearchFiltersDialog(
            initial = eggiku.filters,
            languages = languages,
            onDismissRequest = { showFilters = false },
            onConfirm = {
                showFilters = false
                actions.onSetFilters(it)
            },
        )
    }
}

@Composable
private fun Chip(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = icon?.let {
            { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EggikuSearchFiltersDialog(
    initial: EggikuSearchFilters,
    languages: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (EggikuSearchFilters) -> Unit,
) {
    var filters by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.search_filters)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (languages.size > 1) {
                    Text(stringResource(KMR.strings.search_filter_language), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = lang in filters.languages,
                                onClick = {
                                    filters = filters.copy(
                                        languages = if (lang in filters.languages) filters.languages - lang else filters.languages + lang,
                                    )
                                },
                                label = { Text(LocaleHelper.getLocalizedDisplayName(lang)) },
                            )
                        }
                    }
                }
                Text(stringResource(KMR.strings.search_filter_status), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SManga.ONGOING.toLong() to KMR.strings.search_status_ongoing,
                        SManga.COMPLETED.toLong() to KMR.strings.search_status_completed,
                        SManga.ON_HIATUS.toLong() to KMR.strings.search_status_hiatus,
                    ).forEach { (status, label) ->
                        FilterChip(
                            selected = status in filters.statuses,
                            onClick = {
                                filters = filters.copy(
                                    statuses = if (status in filters.statuses) filters.statuses - status else filters.statuses + status,
                                )
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filters = filters.copy(notInLibrary = !filters.notInLibrary) },
                ) {
                    Checkbox(checked = filters.notInLibrary, onCheckedChange = { filters = filters.copy(notInLibrary = it) })
                    Text(stringResource(KMR.strings.search_filter_not_in_library))
                }
                Text(
                    text = if (filters.minChapters > 0) {
                        stringResource(KMR.strings.search_filter_min_chapters, filters.minChapters)
                    } else {
                        stringResource(KMR.strings.search_filter_min_chapters_off)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = filters.minChapters.toFloat(),
                    onValueChange = { filters = filters.copy(minChapters = (it / 5).toInt() * 5) },
                    valueRange = 0f..200f,
                )
                Text(
                    stringResource(KMR.strings.search_filter_unknown_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(filters) }) { Text(stringResource(KMR.strings.search_filters_apply)) } },
        dismissButton = {
            TextButton(onClick = { onConfirm(EggikuSearchFilters()) }) { Text(stringResource(KMR.strings.search_filters_clear)) }
        },
    )
}

/** Above the source rows: other names being searched, skipped sources, and matches in your stuff */
fun LazyListScope.eggikuSearchHeader(
    eggiku: EggikuSearchState,
    actions: EggikuSearchActions,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    selection: List<Manga>,
) {
    if (eggiku.altTitles.isNotEmpty()) {
        item(key = "eggiku-alt") {
            Text(
                text = stringResource(KMR.strings.search_alt_titles_also, eggiku.altTitles.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
    if (eggiku.skipped.isNotEmpty()) {
        item(key = "eggiku-skipped") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(KMR.strings.search_skipped, eggiku.skipped.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onSearchSkipped) { Text(stringResource(KMR.strings.search_skipped_anyway)) }
            }
        }
    }
    val mine = eggiku.mine
    if (eggiku.includeMine && mine != null && !mine.isEmpty) {
        val owned = mine.library + mine.history
        if (owned.isNotEmpty()) {
            item(key = "eggiku-mine") {
                GlobalSearchResultItem(
                    title = stringResource(KMR.strings.search_mine_title),
                    subtitle = stringResource(KMR.strings.search_mine_subtitle, mine.library.size, mine.history.size),
                    onClick = {},
                ) {
                    GlobalSearchCardRow(
                        titles = owned,
                        getManga = getManga,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                        selection = selection,
                    )
                }
            }
        }
        if (mine.drops.isNotEmpty()) {
            item(key = "eggiku-mine-drops") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(stringResource(KMR.strings.egg_drop_inbox), style = MaterialTheme.typography.titleSmall)
                    mine.drops.take(5).forEach { received ->
                        AssistChip(
                            onClick = { actions.onOpenDrop(received.drop) },
                            leadingIcon = { Icon(Icons.Outlined.Egg, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = {
                                Text(
                                    stringResource(
                                        KMR.strings.search_mine_drop,
                                        received.drop.title,
                                        received.drop.from.ifBlank { stringResource(KMR.strings.compare_friend) },
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One card per series, with every source it was found on */
fun LazyListScope.eggikuMergedResults(
    series: List<EggikuMergedSeries>,
    eggiku: EggikuSearchState,
    actions: EggikuSearchActions,
    onClickItem: (Manga) -> Unit,
) {
    items(series, key = { "eggiku-merged-${it.key}" }) { item ->
        MergedCard(item, eggiku, actions, onClickItem)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergedCard(
    series: EggikuMergedSeries,
    eggiku: EggikuSearchState,
    actions: EggikuSearchActions,
    onClickItem: (Manga) -> Unit,
) {
    val best = EggikuSearchFiltering.best(series, eggiku.details)
    val comparing = series.entries.any { it.second.id in eggiku.comparing }
    val compared = series.entries.all { it.second.id in eggiku.details }
    Card(
        onClick = { onClickItem(best ?: series.entries.first().second) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            MangaCover.Book(data = series.entries.first().second, modifier = Modifier.width(64.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(series.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (series.inLibrary) {
                        stringResource(KMR.strings.search_merged_sources_library, series.entries.size)
                    } else {
                        stringResource(KMR.strings.search_merged_sources, series.entries.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    series.entries.forEach { (source, manga) ->
                        SourceChip(source, eggiku.details[manga.id], isBest = manga.id == best?.id) { onClickItem(manga) }
                    }
                }
                if (series.entries.size > 1 && !compared) {
                    OutlinedButton(onClick = { actions.onCompare(series.entries) }, enabled = !comparing) {
                        if (comparing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(KMR.strings.search_merged_compare))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: Source, details: EggikuSeriesDetails?, isBest: Boolean, onClick: () -> Unit) {
    val extra = when {
        details == null -> ""
        details.failed -> " · ?"
        else -> " · " + stringResource(KMR.strings.search_merged_chapters, details.chapters)
    }
    AssistChip(
        onClick = onClick,
        leadingIcon = if (isBest) {
            { Icon(Icons.Outlined.WorkspacePremium, contentDescription = stringResource(KMR.strings.search_merged_best), modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        label = { Text(source.name + extra, maxLines = 1) },
    )
}
// KMK <--
