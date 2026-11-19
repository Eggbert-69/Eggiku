package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuEggHunt
import eu.kanade.tachiyomi.data.achievements.EggikuHiddenEgg
import eu.kanade.tachiyomi.data.search.EggikuSearchGroups
import eu.kanade.tachiyomi.data.search.EggikuSourceHealth
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Eggiku: your search groups (sets of sources to search together) */
class EggikuSearchGroupsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val raw by EggikuSearchGroups.pref().collectAsState()
        val groups = remember(raw) { EggikuSearchGroups.parse(raw) }
        var confirmDelete by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.search_groups),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(KMR.strings.search_group_new)) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = { navigator.push(EggikuSearchGroupEditScreen(null)) },
                )
            },
        ) { padding ->
            if (groups.isEmpty()) {
                Box(modifier = Modifier.padding(padding)) {
                    EmptyScreen(message = stringResource(KMR.strings.search_groups_empty))
                    EggikuHiddenEgg(EggikuEggHunt.Egg.GROUPS, modifier = Modifier.align(Alignment.BottomCenter))
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    Card(
                        onClick = { navigator.push(EggikuSearchGroupEditScreen(group.id)) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.animateItem(),
                    ) {
                        Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderSpecial, contentDescription = null)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(group.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(KMR.strings.search_group_sources, group.sourceIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (confirmDelete == group.id) {
                                        EggikuSearchGroups.delete(group.id)
                                        if (EggikuSearchGroups.selected().get() == group.id) EggikuSearchGroups.selected().set("")
                                        confirmDelete = null
                                    } else {
                                        confirmDelete = group.id
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(KMR.strings.egg_drop_delete),
                                    tint = if (confirmDelete == group.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(key = "egg") { EggikuHiddenEgg(EggikuEggHunt.Egg.GROUPS) }
            }
        }
    }
}

/** Name a group and tick its sources; "all" buttons per language make "English" or "Raws" quick */
class EggikuSearchGroupEditScreen(private val groupId: String?) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val existing = remember { groupId?.let { id -> EggikuSearchGroups.all().firstOrNull { it.id == id } } }
        var name by remember { mutableStateOf(existing?.name.orEmpty()) }
        var picked by remember { mutableStateOf(existing?.sourceIds?.toSet().orEmpty()) }
        val byLanguage = remember {
            Injekt.get<SourceManager>().getVisibleSources()
                .sortedBy { it.name.lowercase() }
                .groupBy { it.lang }
                .toSortedMap(compareBy { LocaleHelper.getLocalizedDisplayName(it) })
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(if (existing == null) KMR.strings.search_group_new else KMR.strings.search_group_edit),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            enabled = name.isNotBlank() && picked.isNotEmpty(),
                            onClick = {
                                EggikuSearchGroups.upsert(
                                    (existing ?: EggikuSearchGroups.Group(name = name.trim()))
                                        .copy(name = name.trim(), sourceIds = picked.toList()),
                                )
                                navigator.pop()
                            },
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(KMR.strings.search_group_save))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text(stringResource(KMR.strings.search_group_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
                byLanguage.forEach { (lang, sources) ->
                    item(key = "lang-$lang") {
                        val ids = sources.map { it.id }.toSet()
                        val all = picked.containsAll(ids)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LocaleHelper.getLocalizedDisplayName(lang),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { picked = if (all) picked - ids else picked + ids }) {
                                Text(stringResource(if (all) KMR.strings.search_group_none else KMR.strings.search_group_all))
                            }
                        }
                    }
                    items(sources, key = { "source-${it.id}" }) { source ->
                        val health = remember(source.id) { EggikuSourceHealth.get(source.id) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (source.id in picked) picked - source.id else picked + source.id }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = source.id in picked,
                                onCheckedChange = { picked = if (it) picked + source.id else picked - source.id },
                            )
                            Text(source.name, modifier = Modifier.weight(1f))
                            if (health.samples > 0) {
                                Text(
                                    String.format(java.util.Locale.getDefault(), "%.1f s", health.avgMs / 1000f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
