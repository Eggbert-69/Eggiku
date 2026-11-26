package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.tachiyomi.data.shelves.EggikuShelves
import eu.kanade.tachiyomi.ui.library.EggikuShelvesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// KMK -->

/**
 * Eggiku series card: how long to catch up at your pace, the dropped / waiting / reread buttons,
 * and the series' universe (sequels, prequels, spin-offs and side stories) in reading order.
 */
@Composable
fun EggikuShelfCard(manga: Manga, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val raw by EggikuShelves.pref().collectAsState()
    val store = remember(raw) { EggikuShelves.parse(raw) }
    var refresh by remember { mutableIntStateOf(0) }

    // Unread chapters (refreshed when a reread starts or ends)
    val unread by produceState(-1, manga.id, refresh, store.rereads[manga.id]?.active) {
        value = withIOContext { Injekt.get<GetChaptersByMangaId>().await(manga.id).count { !it.read } }
    }
    val universeIds = remember(store.links, manga.id) { EggikuShelves.universe(manga.id, store.links) }
    val universe by produceState<List<Manga>>(emptyList(), universeIds) {
        value = withIOContext {
            if (universeIds.size < 2) return@withIOContext emptyList()
            val getManga = Injekt.get<GetManga>()
            val byId = universeIds.mapNotNull { getManga.await(it) }.associateBy { it.id }
            EggikuShelves.readingOrder(byId.keys, store.links) { byId[it]?.title.orEmpty() }.mapNotNull { byId[it] }
        }
    }
    val found = store.found.filter { it.parentId == manga.id }

    var dropDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var rereadDialog by remember { mutableStateOf(false) }

    val dropped = store.dropped[manga.id]
    val waiting = manga.id in store.waiting
    val reread = store.rereads[manga.id]
    val finished = EggikuShelves.isFinished(manga.status)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (manga.favorite && unread > 0) {
                val perChapter = remember(refresh) { EggikuShelves.secondsPerChapter() }
                val seconds = EggikuShelves.bingeSeconds(unread.toLong(), perChapter)
                val doneBy = remember(seconds) { EggikuShelves.doneBy(seconds) }
                val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
                Text(
                    text = if (finished && waiting) {
                        stringResource(KMR.strings.shelf_waiting_done, EggikuShelves.formatTime(context, seconds))
                    } else {
                        stringResource(KMR.strings.shelf_binge, EggikuShelves.formatTime(context, seconds))
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                doneBy?.let {
                    Text(
                        text = stringResource(KMR.strings.shelf_done_by, it.format(dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!finished || waiting) {
                    FilterChip(
                        selected = waiting,
                        onClick = { EggikuShelves.setWaiting(manga.id, !waiting) },
                        label = { Text(stringResource(KMR.strings.shelf_waiting)) },
                        leadingIcon = { Icon(Icons.Outlined.HourglassEmpty, contentDescription = null) },
                    )
                }
                FilterChip(
                    selected = dropped != null,
                    onClick = { if (dropped != null) EggikuShelves.undrop(manga.id) else dropDialog = true },
                    label = {
                        Text(
                            if (dropped != null) {
                                stringResource(
                                    KMR.strings.shelf_dropped_because,
                                    stringResource(EggikuShelvesScreen.reasonLabel(dropped.reasonEnum)),
                                )
                            } else {
                                stringResource(KMR.strings.drop_series)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.ThumbDown, contentDescription = null) },
                )
                if (manga.favorite) {
                    FilterChip(
                        selected = reread?.active == true,
                        onClick = { rereadDialog = true },
                        label = {
                            Text(
                                when {
                                    reread?.active == true -> stringResource(KMR.strings.reread_active)
                                    (reread?.count ?: 0) > 0 -> stringResource(KMR.strings.reread_again, reread!!.count)
                                    else -> stringResource(KMR.strings.reread_start_chip)
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                    )
                }
            }

            // Universe
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(KMR.strings.universe_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { linkDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(KMR.strings.universe_link))
                }
            }
            if (universe.isEmpty()) {
                Text(
                    text = stringResource(KMR.strings.universe_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            universe.forEachIndexed { index, entry ->
                val isThis = entry.id == manga.id
                val kind = if (isThis) null else EggikuShelves.kindOf(manga.id, entry.id, store.links)
                val mainStory = !isThis && store.links.any { link ->
                    link.from == entry.id && link.to == manga.id &&
                        (link.kindEnum == EggikuShelves.Kind.SPINOFF || link.kindEnum == EggikuShelves.Kind.SIDE_STORY)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isThis) { navigator?.push(MangaScreen(entry.id)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isThis) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                isThis -> stringResource(KMR.strings.universe_this)
                                kind != null -> stringResource(kindLabel(kind))
                                mainStory -> stringResource(KMR.strings.universe_main_story)
                                else -> stringResource(KMR.strings.universe_related)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isThis && store.links.any { it.involves(manga.id, entry.id) }) {
                        IconButton(onClick = { EggikuShelves.unlink(manga.id, entry.id) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.universe_unlink))
                        }
                    }
                }
            }
            found.forEach { candidate ->
                Text(
                    text = stringResource(KMR.strings.universe_found, candidate.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        navigator?.push(EggikuShelvesScreen(startShelf = SPINOFFS_SHELF))
                    },
                )
            }
        }
    }

    if (dropDialog) {
        EggikuDropDialog(
            onDismiss = { dropDialog = false },
            onDrop = { reason, note ->
                dropDialog = false
                scope.launch {
                    val total = withIOContext { Injekt.get<GetChaptersByMangaId>().await(manga.id).size.toLong() }
                    EggikuShelves.drop(manga.id, reason, note, total, manga.status)
                }
            },
        )
    }
    if (rereadDialog) {
        val active = reread?.active == true
        AlertDialog(
            onDismissRequest = { rereadDialog = false },
            title = {
                Text(stringResource(if (active) KMR.strings.reread_end_title else KMR.strings.reread_start_title))
            },
            text = { Text(stringResource(if (active) KMR.strings.reread_end_text else KMR.strings.reread_start_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        rereadDialog = false
                        scope.launch {
                            if (active) {
                                EggikuShelves.endReread(manga.id, finished = true)
                            } else {
                                EggikuShelves.startReread(manga.id)
                            }
                            refresh++
                        }
                    },
                ) {
                    Text(stringResource(if (active) KMR.strings.reread_end_done else KMR.strings.reread_start))
                }
            },
            dismissButton = {
                if (active) {
                    TextButton(
                        onClick = {
                            rereadDialog = false
                            scope.launch {
                                EggikuShelves.endReread(manga.id, finished = false)
                                refresh++
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.reread_end_cancel))
                    }
                } else {
                    TextButton(onClick = { rereadDialog = false }) { Text(stringResource(MR.strings.action_cancel)) }
                }
            },
        )
    }
    if (linkDialog) {
        EggikuLinkDialog(
            mangaId = manga.id,
            onDismiss = { linkDialog = false },
            onLink = { other, kind ->
                linkDialog = false
                EggikuShelves.link(manga.id, other, kind)
            },
        )
    }
}

private const val SPINOFFS_SHELF = 3

private fun EggikuShelves.Link.involves(a: Long, b: Long) = (from == a && to == b) || (from == b && to == a)

private fun kindLabel(kind: EggikuShelves.Kind) = when (kind) {
    EggikuShelves.Kind.SEQUEL -> KMR.strings.universe_sequel
    EggikuShelves.Kind.PREQUEL -> KMR.strings.universe_prequel
    EggikuShelves.Kind.SPINOFF -> KMR.strings.universe_spinoff
    EggikuShelves.Kind.SIDE_STORY -> KMR.strings.universe_side_story
}

@Composable
fun EggikuDropDialog(onDismiss: () -> Unit, onDrop: (EggikuShelves.Reason, String) -> Unit) {
    var reason by remember { mutableStateOf<EggikuShelves.Reason?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.drop_why)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EggikuShelves.Reason.entries.forEach { option ->
                        FilterChip(
                            selected = reason == option,
                            onClick = { reason = option },
                            label = { Text(stringResource(EggikuShelvesScreen.reasonLabel(option))) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text(stringResource(KMR.strings.drop_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(KMR.strings.drop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDrop(reason ?: EggikuShelves.Reason.OTHER, note) }) {
                Text(stringResource(KMR.strings.drop_series))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun EggikuLinkDialog(mangaId: Long, onDismiss: () -> Unit, onLink: (Long, EggikuShelves.Kind) -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Manga?>(null) }
    val library by produceState<List<Manga>>(emptyList()) {
        value = withIOContext {
            Injekt.get<GetLibraryManga>().await().map { it.manga }.distinctBy { it.id }.filter { it.id != mangaId }
        }
    }
    val choice = picked
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (choice == null) KMR.strings.universe_pick else KMR.strings.universe_what_is_it))
        },
        text = {
            if (choice == null) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(KMR.strings.universe_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val matches = remember(query, library) {
                        val q = query.trim()
                        library.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
                            .sortedBy { it.title.lowercase() }
                            .take(MAX_MATCHES)
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(matches, key = { it.id }) { entry ->
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { picked = entry }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            } else {
                Column {
                    Text(
                        text = choice.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    EggikuShelves.Kind.entries.forEach { kind ->
                        Text(
                            text = stringResource(kindLabel(kind)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLink(choice.id, kind) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (choice == null) onDismiss() else picked = null }) {
                Text(stringResource(if (choice == null) MR.strings.action_cancel else KMR.strings.bulk_back))
            }
        },
    )
}

private const val MAX_MATCHES = 60
// KMK <--
