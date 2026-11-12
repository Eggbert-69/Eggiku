package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->

/**
 * Eggiku's extra filters in the library filter sheet: status, missing source, reading recency and genres.
 */
@Composable
fun EggikuFilters(screenModel: LibrarySettingsScreenModel) {
    val prefs = screenModel.libraryPreferences

    val ongoing by prefs.filterOngoing().collectAsState()
    TriStateItem(
        label = stringResource(KMR.strings.filter_ongoing),
        state = ongoing,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterOngoing) },
    )
    val paused by prefs.filterPausedOrCancelled().collectAsState()
    TriStateItem(
        label = stringResource(KMR.strings.filter_paused_cancelled),
        state = paused,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterPausedOrCancelled) },
    )
    val notRecent by prefs.filterNotReadRecently().collectAsState()
    TriStateItem(
        label = stringResource(KMR.strings.filter_not_read_recently),
        state = notRecent,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterNotReadRecently) },
    )
    val noNew by prefs.filterNoNewChapters().collectAsState()
    TriStateItem(
        label = stringResource(KMR.strings.filter_no_new_chapters),
        state = noNew,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterNoNewChapters) },
    )
    val missing by prefs.filterMissingSource().collectAsState()
    TriStateItem(
        label = stringResource(KMR.strings.filter_missing_source),
        state = missing,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterMissingSource) },
    )

    GenreFilter(screenModel)
}

@Composable
private fun GenreFilter(screenModel: LibrarySettingsScreenModel) {
    val genres by screenModel.libraryGenresFlow.collectAsState()
    if (genres.isEmpty()) return

    val included by screenModel.libraryPreferences.filterGenresInclude().collectAsState()
    val excluded by screenModel.libraryPreferences.filterGenresExclude().collectAsState()
    val includedKeys = included.mapTo(mutableSetOf()) { it.lowercase() }
    val excludedKeys = excluded.mapTo(mutableSetOf()) { it.lowercase() }
    val activeCount = includedKeys.size + excludedKeys.size

    var expanded by rememberSaveable { mutableStateOf(activeCount > 0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = SettingsItemsPaddings.Horizontal, vertical = SettingsItemsPaddings.Vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (activeCount > 0) {
                    "${stringResource(KMR.strings.filter_genres)} ($activeCount)"
                } else {
                    stringResource(KMR.strings.filter_genres)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(KMR.strings.filter_genres_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = stringResource(KMR.strings.filter_genres),
        )
    }

    if (!expanded) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal - 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        genres.forEach { genre ->
            val key = genre.lowercase()
            val state = when (key) {
                in includedKeys -> TriState.ENABLED_IS
                in excludedKeys -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
            FilterChip(
                selected = state != TriState.DISABLED,
                onClick = { screenModel.toggleGenre(genre) },
                label = { Text(genre) },
                leadingIcon = when (state) {
                    TriState.ENABLED_IS -> {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                    TriState.ENABLED_NOT -> {
                        { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                    TriState.DISABLED -> null
                },
                colors = if (state == TriState.ENABLED_NOT) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }

    if (activeCount > 0) {
        TextButton(
            onClick = screenModel::clearGenres,
            modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal - 12.dp),
        ) {
            Text(stringResource(KMR.strings.filter_genres_clear))
        }
    }
}
// KMK <--
