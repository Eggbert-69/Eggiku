package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
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
                ContinueReadingItem(
                    item = item,
                    onResume = { onResume(item.libraryManga) },
                    onOpen = { onOpen(item.libraryManga.manga.id) },
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingItem(
    item: LibraryItem,
    onResume: () -> Unit,
    onOpen: () -> Unit,
) {
    val manga = item.libraryManga.manga
    Column(
        modifier = Modifier
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
            text = stringResource(
                KMR.strings.continue_reading_unread,
                item.libraryManga.unreadCount.toInt(),
            ),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
// KMK <--
