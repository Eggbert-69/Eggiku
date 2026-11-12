package eu.kanade.presentation.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.cook.EggikuCookTimer
import eu.kanade.tachiyomi.ui.library.EggikuContinueReading
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

// KMK -->

/**
 * Eggiku: a row of recently read library entries that still have unread chapters.
 * Tap resumes the next chapter, press-and-hold opens the entry.
 */
@Composable
fun ContinueReadingRow(
    items: List<LibraryItem>,
    onResume: (LibraryManga) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cards swiped away disappear right away, before the library refreshes
    var dismissed by remember { mutableStateOf(emptySet<Long>()) }
    val context = LocalContext.current
    val cooked by EggikuCookTimer.ready().collectAsState()

    @Suppress("NAME_SHADOWING")
    val items = items.filterNot { it.libraryManga.id in dismissed }
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(KMR.strings.continue_reading_row_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = items,
                key = { "continue-reading-${it.id}" },
            ) { item ->
                val id = item.libraryManga.id
                val lastRead = item.libraryManga.lastRead
                val tag = when {
                    id.toString() in cooked -> stringResource(KMR.strings.continue_reading_cooked)
                    lastRead > 0 && System.currentTimeMillis() - lastRead > PICK_BACK_UP_AFTER_MS ->
                        stringResource(KMR.strings.continue_reading_picked_up)
                    else -> null
                }
                ContinueReadingItem(
                    item = item,
                    tag = tag,
                    onResume = { onResume(item.libraryManga) },
                    onOpen = { onOpen(item.libraryManga.manga.id) },
                    onSkip = {
                        EggikuContinueReading.skip(id)
                        dismissed = dismissed + id
                        context.toast(KMR.strings.continue_reading_skipped)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingItem(
    item: LibraryItem,
    tag: String?,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manga = item.libraryManga.manga
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    Column(
        modifier = modifier
            // Swipe a card up to hide it for a week
            .graphicsLayer {
                translationY = drag.value
                alpha = (1f + drag.value / (threshold * 2)).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (drag.value < -threshold) {
                                drag.animateTo(-threshold * 3, tween(160))
                                onSkip()
                            } else {
                                drag.animateTo(0f, spring(dampingRatio = 0.6f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.6f)) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo((drag.value + amount).coerceAtMost(0f)) }
                }
            }
            .width(92.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onResume,
                onLongClick = onOpen,
            )
            .padding(2.dp),
    ) {
        Box {
            MangaCover.Book(
                data = MangaCoverModel(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    ogUrl = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                contentDescription = manga.title,
                modifier = Modifier.fillMaxWidth(),
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(KMR.strings.continue_reading_row_title),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(4.dp),
            )
        }
        Text(
            text = manga.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = tag ?: stringResource(
                KMR.strings.continue_reading_unread,
                item.libraryManga.unreadCount.toInt(),
            ),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
private const val PICK_BACK_UP_AFTER_MS = 14L * 24 * 60 * 60 * 1000

/** Eggiku: series with a cook timer running, with how long is left */
@Composable
fun CookingRow(
    items: List<LibraryItem>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerEntries by EggikuCookTimer.timers().collectAsState()
    val goalEntries by EggikuCookTimer.chapterGoalsPref().collectAsState()
    val timers = remember(timerEntries) { EggikuCookTimer.parse(timerEntries) }
    val goals = remember(goalEntries) { EggikuCookTimer.chapterGoals(goalEntries) }

    @Suppress("NAME_SHADOWING")
    val items = items.filter { it.libraryManga.id in timers || it.libraryManga.id in goals }
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = "🍳 " + stringResource(KMR.strings.cooking_row_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = items, key = { "cooking-${it.id}" }) { item ->
                val id = item.libraryManga.id
                val manga = item.libraryManga.manga
                val left = timers[id]?.let { due ->
                    val days = ((due - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
                    if (days >= 14) {
                        stringResource(KMR.strings.cooking_weeks_left, (days / 7).toInt())
                    } else {
                        stringResource(KMR.strings.cooking_days_left, days.toInt())
                    }
                } ?: goals[id]?.let { (goal, base) ->
                    val total = item.libraryManga.totalChapters.toInt()
                    stringResource(KMR.strings.cooking_chapters_left, (goal - (total - base)).coerceAtLeast(0))
                }.orEmpty()
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(onClick = { onOpen(manga.id) })
                        .padding(2.dp),
                ) {
                    MangaCover.Book(
                        data = MangaCoverModel(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            ogUrl = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        contentDescription = manga.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 0.85f },
                    )
                    Text(
                        text = left,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
// KMK <--
