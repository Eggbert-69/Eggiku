package eu.kanade.presentation.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.search.EggikuSearchHistory
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
/** Eggiku: saved and recent searches, shown before a search is run. Hold one to save or unsave it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EggikuSearchHistoryPanel(contentPadding: PaddingValues, onPick: (String) -> Unit) {
    val recentRaw by EggikuSearchHistory.recentPref().collectAsState()
    val savedRaw by EggikuSearchHistory.savedPref().collectAsState()
    val saved = remember(savedRaw) { EggikuSearchHistory.parse(savedRaw) }
    val recent = remember(recentRaw, saved) { EggikuSearchHistory.parse(recentRaw).filterNot { it in saved } }
    if (saved.isEmpty() && recent.isEmpty()) return

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (saved.isNotEmpty()) {
            Text(stringResource(KMR.strings.search_saved), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { q -> Chip(q, starred = true, onPick = onPick) }
            }
        }
        if (recent.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(KMR.strings.search_recent), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = EggikuSearchHistory::clearRecent) { Text(stringResource(KMR.strings.search_clear)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { q -> Chip(q, starred = false, onPick = onPick) }
            }
        }
        Text(
            stringResource(KMR.strings.search_hold_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(query: String, starred: Boolean, onPick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (starred) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(
            onClick = { onPick(query) },
            onLongClick = { EggikuSearchHistory.toggleSaved(query) },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (starred) Icons.Filled.Star else Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.padding(0.dp),
            )
            Text(query, style = MaterialTheme.typography.labelLarge)
        }
    }
}
// KMK <--
