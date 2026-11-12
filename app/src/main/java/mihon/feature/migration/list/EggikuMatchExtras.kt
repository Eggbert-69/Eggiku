package mihon.feature.migration.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.util.lang.toRelativeString
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// KMK -->
/** Eggiku: the "check this match" warning and the "compare matches" button under a migration row */
@Composable
internal fun EggikuMatchExtras(
    item: MigratingManga,
    onConfirm: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val result by item.searchResult.collectAsState()
    val unsure by item.eggikuUnsure.collectAsState()
    val confirmed by item.eggikuConfirmed.collectAsState()
    val candidates by item.eggikuCandidates.collectAsState()
    val success = result as? MigratingManga.SearchResult.Success ?: return
    var comparing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (unsure && !confirmed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConfirm),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(KMR.strings.eggiku_migration_unsure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (candidates.size > 1) {
            TextButton(onClick = { comparing = true }) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                Text(
                    text = stringResource(KMR.strings.eggiku_migration_compare, candidates.size),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (comparing) {
        AlertDialog(
            onDismissRequest = { comparing = false },
            title = { Text(stringResource(KMR.strings.eggiku_migration_compare_title)) },
            text = {
                LazyColumn {
                    itemsIndexed(candidates) { index, candidate ->
                        val picked = candidate.manga.id == success.manga.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    comparing = false
                                    if (!picked) onPick(candidate.manga.id)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = buildString {
                                    append(candidate.source)
                                    if (index == 0) append("  ·  ").append(stringResource(KMR.strings.eggiku_migration_best))
                                    if (picked) append("  ✓")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (picked) FontWeight.Bold else null,
                            )
                            Text(candidate.manga.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            val context = LocalContext.current
                            val updated = if (candidate.lastUploadMillis > 0) {
                                LocalDate.ofInstant(Instant.ofEpochMilli(candidate.lastUploadMillis), ZoneId.systemDefault())
                                    .toRelativeString(context)
                            } else {
                                "?"
                            }
                            Text(
                                text = stringResource(
                                    KMR.strings.eggiku_migration_candidate_line,
                                    candidate.chapterCount,
                                    candidate.latestChapter?.let(::formatChapterNumber) ?: "?",
                                    updated,
                                    candidate.carriedRead,
                                    candidate.totalRead,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { comparing = false }) {
                    Text(stringResource(KMR.strings.eggiku_close))
                }
            },
        )
    }
}
// KMK <--
