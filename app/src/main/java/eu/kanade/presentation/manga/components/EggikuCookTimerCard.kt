package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.cook.EggikuCookTimer
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// KMK -->

/** "Until X new chapters" choices */
private val CookChapterChoices = listOf(5, 10, 20)

/** Choices in weeks: 2 weeks, then 1, 2, 3, 6 and 12 months */
private val CookChoices = listOf(2, 4, 9, 13, 26, 52)

/** Eggiku: "let it cook" card on a series page */
@Composable
fun EggikuCookTimerCard(mangaId: Long, modifier: Modifier = Modifier) {
    val timers by EggikuCookTimer.timers().collectAsState()
    val ready by EggikuCookTimer.ready().collectAsState()
    val due = remember(timers) { EggikuCookTimer.parse(timers)[mangaId] }
    val goalEntries by EggikuCookTimer.chapterGoalsPref().collectAsState()
    val goal = remember(goalEntries) { EggikuCookTimer.chapterGoals(goalEntries)[mangaId] }
    val scope = rememberCoroutineScope()
    val chaptersSoFar by produceState(0, goal) {
        value = if (goal == null) 0 else (EggikuCookTimer.chapterCount(mangaId) - goal.second).coerceAtLeast(0)
    }
    val isReady = mangaId.toString() in ready
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(KMR.strings.cook_pick_title)) },
            text = {
                Column {
                    Text(
                        stringResource(KMR.strings.cook_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Wait for chapters instead of time
                    CookChapterChoices.forEach { chapters ->
                        Text(
                            text = pluralStringResource(KMR.plurals.cook_until_chapters, chapters, chapters),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { EggikuCookTimer.startChapters(mangaId, chapters) }
                                    picking = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    CookChoices.forEach { weeks ->
                        Text(
                            text = cookLabel(weeks),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    EggikuCookTimer.start(mangaId, weeks)
                                    picking = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(MR.strings.action_cancel)) }
            },
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isReady) "🍳" else "⏲️",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.eggikuPop(Triple(due, goal, isReady), peak = 1.3f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(KMR.strings.cook_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when {
                        isReady -> stringResource(KMR.strings.cook_ready)
                        goal != null -> stringResource(KMR.strings.cook_until_chapters_status, goal.first, chaptersSoFar)
                        due != null -> stringResource(KMR.strings.cook_until, formatDate(due))
                        else -> stringResource(KMR.strings.cook_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val cooking = due != null || goal != null
            if (cooking || isReady) {
                TextButton(onClick = { EggikuCookTimer.cancel(mangaId) }) {
                    Text(stringResource(if (isReady) KMR.strings.cook_dismiss else MR.strings.action_cancel))
                }
            }
            OutlinedButton(onClick = { picking = true }) {
                Text(stringResource(if (cooking) KMR.strings.cook_change else KMR.strings.cook_start))
            }
        }
    }
}

@Composable
private fun cookLabel(weeks: Int): String = when {
    weeks < 4 -> pluralStringResource(KMR.plurals.cook_weeks, weeks, weeks)
    else -> {
        val months = Math.round(weeks / 4.345f)
        pluralStringResource(KMR.plurals.cook_months, months, months)
    }
}

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
// KMK <--
