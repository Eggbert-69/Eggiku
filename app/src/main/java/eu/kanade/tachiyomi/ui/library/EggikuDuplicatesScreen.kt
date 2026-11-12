package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.duplicates.EggikuDuplicates
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.IconButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

// KMK -->

/** Eggiku duplicate skimmer: review same-name series, pick the keeper, merge */
class EggikuDuplicatesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val sourceManager = remember { Injekt.get<SourceManager>() }
        var reload by remember { mutableIntStateOf(0) }
        var working by remember { mutableStateOf(false) }

        val groups by produceState<List<EggikuDuplicates.Group>?>(null, reload) {
            value = EggikuDuplicates.findGroups()
        }
        // Per group: chosen keeper id, and whether to merge the group at all
        val keepers = remember(groups) { mutableStateMapOf<String, Long>() }
        val skipped = remember(groups) { mutableStateMapOf<String, Boolean>() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.duplicates_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            enabled = EggikuDuplicates.undoable().isNotEmpty() && !working,
                            onClick = {
                                scope.launch {
                                    working = true
                                    val restored = EggikuDuplicates.undoLast()
                                    context.toast(context.stringResource(KMR.strings.duplicates_undone, restored))
                                    working = false
                                    reload++
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = stringResource(KMR.strings.duplicates_undo),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                val list = groups.orEmpty()
                val toMerge = list.count { skipped[it.key] != true }
                if (list.isNotEmpty()) {
                    FilledTonalButton(
                        enabled = toMerge > 0 && !working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = {
                            scope.launch {
                                working = true
                                val choices = list.filter { skipped[it.key] != true }.map { group ->
                                    val keeperId = keepers[group.key] ?: group.suggestedKeeper.id
                                    val keeper = group.entries.first { it.id == keeperId }
                                    keeper to group.entries.filter { it.id != keeperId }
                                }
                                val removed = EggikuDuplicates.merge(choices)
                                context.toast(context.stringResource(KMR.strings.duplicates_merged, removed))
                                working = false
                                reload++
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.duplicates_merge, toMerge))
                    }
                }
            },
        ) { padding ->
            val list = groups
            when {
                list == null || working -> LoadingScreen(Modifier.padding(padding))
                list.isEmpty() -> EmptyScreen(
                    stringRes = KMR.strings.duplicates_none,
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            stringResource(KMR.strings.duplicates_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    items(list, key = { it.key }) { group ->
                        val include = skipped[group.key] != true
                        val keeperId = keepers[group.key] ?: group.suggestedKeeper.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = include, onCheckedChange = { skipped[group.key] = !it })
                                    Text(
                                        group.entries.first().manga.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                group.entries.forEach { entry ->
                                    val manga = entry.manga
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = include) { keepers[group.key] = entry.id }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = entry.id == keeperId,
                                            onClick = { keepers[group.key] = entry.id },
                                            enabled = include,
                                        )
                                        MangaCover.Book(
                                            data = MangaCoverModel(
                                                mangaId = manga.id,
                                                sourceId = manga.source,
                                                isMangaFavorite = manga.favorite,
                                                ogUrl = manga.thumbnailUrl,
                                                lastModified = manga.coverLastModified,
                                            ),
                                            modifier = Modifier.width(40.dp),
                                        )
                                        Column(modifier = Modifier.padding(start = 10.dp)) {
                                            Text(
                                                sourceManager.getOrStub(manga.source).name,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                stringResource(
                                                    if (entry.id == keeperId) {
                                                        KMR.strings.duplicates_keep_line
                                                    } else {
                                                        KMR.strings.duplicates_remove_line
                                                    },
                                                    entry.readCount.toInt(),
                                                    entry.totalChapters.toInt(),
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (entry.id == keeperId) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
