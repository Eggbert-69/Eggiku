package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.shelves.EggikuShelfNotifier
import eu.kanade.tachiyomi.data.shelves.EggikuShelves
import eu.kanade.tachiyomi.data.shelves.EggikuUniverseSync
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

// KMK -->

/** Eggiku shelves: dropped, waiting to finish, quickest to finish, spin-offs found, and rereads */
class EggikuShelvesScreen(private val startShelf: Int = 0) : Screen() {

    private enum class Shelf(val title: StringResource) {
        DROPPED(KMR.strings.shelf_dropped),
        WAITING(KMR.strings.shelf_waiting),
        QUICKEST(KMR.strings.shelf_quickest),
        SPINOFFS(KMR.strings.shelf_spinoffs),
        REREADS(KMR.strings.shelf_rereads),
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selected by rememberSaveable { mutableIntStateOf(startShelf.coerceIn(0, Shelf.entries.lastIndex)) }
        var reload by remember { mutableIntStateOf(0) }
        val raw by EggikuShelves.pref().collectAsState()
        val store = remember(raw) { EggikuShelves.parse(raw) }
        val library by produceState<Map<Long, LibraryManga>?>(null, reload) {
            value = withIOContext { Injekt.get<GetLibraryManga>().await().associateBy { it.id } }
        }
        val perChapter = remember(reload) { EggikuShelves.secondsPerChapter() }
        val perDay = remember(reload) { EggikuShelves.secondsPerDay() }
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

        Scaffold(
            topBar = { scrollBehavior ->
                Column {
                    AppBar(
                        title = stringResource(KMR.strings.shelf_title),
                        navigateUp = navigator::pop,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            if (Shelf.entries[selected] == Shelf.SPINOFFS) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            context.toast(context.stringResource(KMR.strings.shelf_spinoffs_looking))
                                            val count = EggikuShelfNotifier.scanForSpinoffs(context, force = true) +
                                                // KMK -->
                                                EggikuUniverseSync.run(context, force = true)
                                            // KMK <--
                                            if (count == 0) context.toast(context.stringResource(KMR.strings.shelf_spinoffs_none_new))
                                        }
                                    },
                                ) {
                                    Icon(Icons.Outlined.Refresh, stringResource(KMR.strings.shelf_spinoffs_look_now))
                                }
                            }
                        },
                    )
                    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                        Shelf.entries.forEachIndexed { index, shelf ->
                            Tab(
                                selected = selected == index,
                                onClick = { selected = index },
                                text = { Text(stringResource(shelf.title)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val lib = library
            if (lib == null) {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            val shelf = Shelf.entries[selected]
            val rows: List<ShelfRow> = when (shelf) {
                Shelf.DROPPED ->
                    store.dropped.entries
                        .sortedByDescending { it.value.at }
                        .mapNotNull { (id, drop) ->
                            val manga = lib[id] ?: return@mapNotNull null
                            val grew = manga.totalChapters - drop.chaptersAt
                            val news = when {
                                EggikuShelves.isFinished(manga.manga.status) && !EggikuShelves.isFinished(drop.statusAt) ->
                                    context.stringResource(KMR.strings.shelf_dropped_finished_since)
                                drop.chaptersAt > 0 && grew >= EggikuShelfNotifier.GREW_BY ->
                                    context.stringResource(KMR.strings.shelf_dropped_grew, grew.toInt())
                                else -> null
                            }
                            val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - drop.at).toInt()
                            ShelfRow(
                                key = "d$id",
                                cover = manga.manga,
                                title = manga.manga.title,
                                lines = listOfNotNull(
                                    context.stringResource(reasonLabel(drop.reasonEnum)) +
                                        drop.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    context.stringResource(KMR.strings.shelf_dropped_ago, days),
                                ),
                                highlight = news?.let { context.stringResource(KMR.strings.shelf_maybe_another_shot, it) },
                                onClick = { navigator.push(MangaScreen(id)) },
                                action = Icons.Outlined.Replay to KMR.strings.shelf_undrop,
                                onAction = { EggikuShelves.undrop(id) },
                            )
                        }
                Shelf.WAITING ->
                    store.waiting.keys
                        .mapNotNull { lib[it] }
                        .sortedWith(
                            compareByDescending<LibraryManga> { EggikuShelves.isFinished(it.manga.status) }
                                .thenBy { it.manga.title.lowercase() },
                        )
                        .map { manga ->
                            val finished = EggikuShelves.isFinished(manga.manga.status)
                            val time = EggikuShelves.formatTime(context, EggikuShelves.bingeSeconds(manga.unreadCount, perChapter))
                            ShelfRow(
                                key = "w${manga.id}",
                                cover = manga.manga,
                                title = manga.manga.title,
                                lines = listOf(context.stringResource(KMR.strings.shelf_unread_time, manga.unreadCount.toInt(), time)),
                                highlight = if (finished) context.stringResource(KMR.strings.shelf_waiting_done, time) else null,
                                onClick = { navigator.push(MangaScreen(manga.id)) },
                                action = Icons.Outlined.Close to KMR.strings.shelf_waiting_remove,
                                onAction = { EggikuShelves.setWaiting(manga.id, false) },
                            )
                        }
                Shelf.QUICKEST ->
                    lib.values
                        .filter { it.hasStarted && it.unreadCount > 0 && it.id !in store.dropped }
                        .sortedBy { it.unreadCount }
                        .take(QUICKEST_SIZE)
                        .map { manga ->
                            val seconds = EggikuShelves.bingeSeconds(manga.unreadCount, perChapter)
                            val doneBy = EggikuShelves.doneBy(seconds, perDay)
                            ShelfRow(
                                key = "q${manga.id}",
                                cover = manga.manga,
                                title = manga.manga.title,
                                lines = listOfNotNull(
                                    context.stringResource(
                                        KMR.strings.shelf_unread_time,
                                        manga.unreadCount.toInt(),
                                        EggikuShelves.formatTime(context, seconds),
                                    ),
                                    doneBy?.let { context.stringResource(KMR.strings.shelf_done_by, it.format(dateFormat)) },
                                ),
                                onClick = { navigator.push(MangaScreen(manga.id)) },
                            )
                        }
                Shelf.SPINOFFS -> store.found.sortedByDescending { it.at }.map { found ->
                    ShelfRow(
                        key = "s${found.key}",
                        cover = found.thumbnail,
                        title = found.title,
                        lines = listOfNotNull(
                            lib[found.parentId]?.let { context.stringResource(KMR.strings.shelf_spinoff_of, it.manga.title) },
                        ),
                        onClick = {
                            scope.launch {
                                val id = openFound(found)
                                navigator.push(MangaScreen(id))
                            }
                        },
                        action = Icons.Outlined.Link to KMR.strings.shelf_spinoff_link,
                        onAction = {
                            scope.launch {
                                val id = openFound(found)
                                EggikuShelves.link(found.parentId, id, EggikuShelves.Kind.SPINOFF)
                                EggikuShelves.dismissFound(found.key)
                                context.toast(context.stringResource(KMR.strings.shelf_spinoff_linked))
                            }
                        },
                        secondAction = Icons.Outlined.Close to KMR.strings.shelf_spinoff_dismiss,
                        onSecondAction = { EggikuShelves.dismissFound(found.key) },
                    )
                }
                Shelf.REREADS ->
                    store.rereads.entries
                        .filter { it.value.count > 0 || it.value.active }
                        .mapNotNull { (id, reread) -> lib[id]?.let { it to reread } }
                        .sortedByDescending { it.second.count }
                        .map { (manga, reread) ->
                            ShelfRow(
                                key = "r${manga.id}",
                                cover = manga.manga,
                                title = manga.manga.title,
                                lines = listOfNotNull(
                                    context.stringResource(KMR.strings.shelf_reread_count, reread.count),
                                    context.stringResource(KMR.strings.shelf_rereading_now).takeIf { reread.active },
                                ),
                                onClick = { navigator.push(MangaScreen(manga.id)) },
                            )
                        }
            }

            if (rows.isEmpty()) {
                EmptyScreen(
                    stringRes = when (shelf) {
                        Shelf.DROPPED -> KMR.strings.shelf_dropped_empty
                        Shelf.WAITING -> KMR.strings.shelf_waiting_empty
                        Shelf.QUICKEST -> KMR.strings.shelf_quickest_empty
                        Shelf.SPINOFFS -> KMR.strings.shelf_spinoffs_empty
                        Shelf.REREADS -> KMR.strings.shelf_rereads_empty
                    },
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 4.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shelf == Shelf.QUICKEST || shelf == Shelf.WAITING) {
                    item(key = "pace") {
                        Text(
                            text = stringResource(
                                KMR.strings.shelf_pace_explain,
                                EggikuShelves.formatTime(context, perChapter),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(rows, key = { it.key }) { row -> ShelfRowItem(row) }
            }
        }
    }

    private class ShelfRow(
        val key: String,
        val cover: Any?,
        val title: String,
        val lines: List<String>,
        val highlight: String? = null,
        val onClick: () -> Unit,
        val action: Pair<ImageVector, StringResource>? = null,
        val onAction: () -> Unit = {},
        val secondAction: Pair<ImageVector, StringResource>? = null,
        val onSecondAction: () -> Unit = {},
    )

    @Composable
    private fun ShelfRowItem(row: ShelfRow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = row.onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(data = row.cover, modifier = Modifier.width(48.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                row.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                row.highlight?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            row.action?.let { (icon, label) ->
                IconButton(onClick = row.onAction) { Icon(icon, contentDescription = stringResource(label)) }
            }
            row.secondAction?.let { (icon, label) ->
                IconButton(onClick = row.onSecondAction) { Icon(icon, contentDescription = stringResource(label)) }
            }
        }
    }

    private suspend fun openFound(found: EggikuShelves.Found): Long = withIOContext {
        val sManga = SManga.create().apply {
            url = found.url
            title = found.title
            thumbnail_url = found.thumbnail
        }
        Injekt.get<NetworkToLocalManga>().invoke(sManga.toDomainManga(found.sourceId), updateInfo = false).id
    }

    companion object {
        private const val QUICKEST_SIZE = 60

        fun reasonLabel(reason: EggikuShelves.Reason): StringResource = when (reason) {
            EggikuShelves.Reason.ART -> KMR.strings.drop_reason_art
            EggikuShelves.Reason.SLOW -> KMR.strings.drop_reason_slow
            EggikuShelves.Reason.STORY -> KMR.strings.drop_reason_story
            EggikuShelves.Reason.LOST_INTEREST -> KMR.strings.drop_reason_lost_interest
            EggikuShelves.Reason.TOO_LONG -> KMR.strings.drop_reason_too_long
            EggikuShelves.Reason.OTHER -> KMR.strings.drop_reason_other
        }
    }
}
// KMK <--
