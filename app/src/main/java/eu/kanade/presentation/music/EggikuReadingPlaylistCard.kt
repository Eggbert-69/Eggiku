package eu.kanade.presentation.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.music.EggikuMusic
import eu.kanade.tachiyomi.data.music.EggikuMusicPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Eggiku: the "Reading playlist" card on a series page */
@Composable
fun EggikuReadingPlaylistCard(
    mangaId: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { Injekt.get<EggikuMusicPreferences>() }
    val seriesPref = remember(mangaId) { preferences.seriesPlaylist(mangaId) }
    val autoStartPref = remember(mangaId) { preferences.seriesAutoStart(mangaId) }
    val seriesLink by seriesPref.collectPrefAsState()
    val autoStart by autoStartPref.collectPrefAsState()
    val categoryEntries by preferences.categoryPlaylists().collectPrefAsState()
    val categoryNames by remember(mangaId) {
        Injekt.get<GetCategories>().subscribe(mangaId).map { list -> list.map { it.name } }
    }.collectAsState(emptyList())

    val categoryMatch = remember(categoryEntries, categoryNames) {
        val map = EggikuMusicPreferences.parseCategoryPlaylists(categoryEntries)
        categoryNames.firstNotNullOfOrNull { name -> map[name]?.let { name to it } }
    }
    val link = seriesLink.ifBlank { categoryMatch?.second.orEmpty() }
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        EggikuPlaylistDialog(
            title = stringResource(KMR.strings.music_reading_playlist),
            initialUrl = seriesLink,
            onDismiss = { editing = false },
            onSave = { seriesPref.set(it) },
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(KMR.strings.music_reading_playlist),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = when {
                    seriesLink.isNotBlank() -> seriesLink
                    categoryMatch != null -> stringResource(KMR.strings.music_playlist_from_category, categoryMatch.first)
                    else -> stringResource(KMR.strings.music_playlist_none_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (link.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                if (!EggikuMusic.openPlaylist(context, link)) {
                                    context.toast(KMR.strings.music_playlist_open_failed)
                                }
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.music_play_in_youtube_music))
                    }
                }
                OutlinedButton(onClick = { editing = true }) {
                    Text(
                        stringResource(
                            if (seriesLink.isBlank()) KMR.strings.music_playlist_add else KMR.strings.music_playlist_change,
                        ),
                    )
                }
            }
            if (link.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(KMR.strings.music_autostart),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStartPref.set(it) },
                    )
                }
            }
        }
    }
}
// KMK <--
