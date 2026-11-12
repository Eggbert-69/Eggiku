package eu.kanade.presentation.more.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Eggiku: streaks, heatmap, weekly goal and this week's numbers at the top of Stats */
@Composable
fun EggikuReadingStatsSection(
    onOpenRecaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val version by EggikuReadingLog.changes.collectAsState()
    val goal by EggikuReadingLog.weeklyGoalChapters().collectPrefAsState()
    val dayStart by EggikuReadingLog.dayStartHour().collectPrefAsState()
    val log = remember(version, dayStart) { EggikuReadingLog.all() }
    val today = remember(version, dayStart) { EggikuReadingLog.today() }
    val streak = remember(log, today) { EggikuReadingLog.streak(log, today) }
    val weekStart = remember(today) { EggikuReadingLog.weekStart(today) }
    val week = remember(log, weekStart, today) { EggikuReadingLog.sum(log, weekStart, today) }
    val allTime = remember(log) { EggikuReadingLog.sum(log, java.time.LocalDate.MIN, java.time.LocalDate.MAX) }
    var showSettings by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }

    if (showSettings) EggikuStatsSettingsDialog(onDismiss = { showSettings = false })
    if (showGoal) EggikuGoalDialog(current = goal, onDismiss = { showGoal = false })

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(KMR.strings.stats_your_reading),
                style = MaterialTheme.typography.titleMedium,
            )

            // Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatBlock(value = "🔥 ${streak.current}", label = stringResource(KMR.strings.stats_streak))
                StatBlock(value = "${streak.longest}", label = stringResource(KMR.strings.stats_longest_streak))
                StatBlock(value = "❄️ ${streak.freezes}", label = stringResource(KMR.strings.stats_freezes))
            }
            if (streak.current > 0 && !streak.readToday) {
                Text(
                    text = stringResource(KMR.strings.stats_streak_today_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Heatmap(log = log, today = today)

            // Weekly goal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGoal = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalRing(progress = if (goal > 0) week.chapters / goal.toFloat() else 0f)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = if (goal > 0) {
                            stringResource(KMR.strings.stats_goal_progress, week.chapters, goal)
                        } else {
                            stringResource(KMR.strings.stats_goal_none)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(KMR.strings.stats_goal_tap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // This week and all time
            Text(stringResource(KMR.strings.stats_this_week), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock(value = "${week.chapters}", label = stringResource(KMR.strings.stats_chapters))
                val (h, m) = EggikuStatsFormat.hoursAndMinutes(week.seconds)
                StatBlock(value = stringResource(KMR.strings.stats_duration, h, m), label = stringResource(KMR.strings.stats_time))
                StatBlock(value = EggikuStatsFormat.distance(week.swipeMm), label = stringResource(KMR.strings.stats_swiped))
            }
            Text(stringResource(KMR.strings.stats_all_time), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock(value = EggikuStatsFormat.distance(allTime.swipeMm), label = stringResource(KMR.strings.stats_swiped))
                StatBlock(value = EggikuStatsFormat.distance(allTime.panMm), label = stringResource(KMR.strings.stats_panned))
                StatBlock(value = "${allTime.zooms}", label = stringResource(KMR.strings.stats_zooms))
                StatBlock(value = "${allTime.taps}", label = stringResource(KMR.strings.stats_taps))
            }
            PaddingLine(allTime.padding)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenRecaps) {
                    Text(stringResource(KMR.strings.stats_recaps))
                }
                OutlinedButton(onClick = { showSettings = true }) {
                    Text(stringResource(MR.strings.action_settings))
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaddingLine(padding: Map<String, Long>) {
    val total = padding.values.sum()
    if (total <= 0) return
    val unfolded = padding.filterKeys { it.startsWith("unfolded") }.values.sum()
    val topUnfolded = padding.filterKeys { it.startsWith("unfolded") }.maxByOrNull { it.value }?.key
        ?.substringAfter(':')
    Text(
        text = stringResource(
            KMR.strings.stats_padding_line,
            (unfolded * 100 / total).toInt(),
            topUnfolded ?: "0",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The last 12 weeks, one square per day, darker for more reading */
@Composable
private fun Heatmap(log: Map<java.time.LocalDate, EggikuReadingLog.DayStats>, today: java.time.LocalDate) {
    val weeks = 12
    val start = EggikuReadingLog.weekStart(today).minusWeeks((weeks - 1).toLong())
    val cells = remember(log, today) {
        (0 until weeks * 7).map { offset ->
            val day = start.plusDays(offset.toLong())
            if (day.isAfter(today)) -1L else (log[day]?.seconds ?: 0L)
        }
    }
    val maxSeconds = (cells.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(weeks / 7f),
    ) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap * (weeks - 1)) / weeks
        cells.forEachIndexed { index, seconds ->
            if (seconds < 0) return@forEachIndexed
            val column = index / 7
            val row = index % 7
            val color = if (seconds == 0L) {
                empty
            } else {
                primary.copy(alpha = 0.25f + 0.75f * (seconds.toFloat() / maxSeconds))
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell / 4),
            )
        }
    }
}

@Composable
private fun GoalRing(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(44.dp)) {
            val stroke = 5.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(stroke))
            drawArc(
                primary,
                -90f,
                360f * progress.coerceIn(0f, 1f),
                false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (progress >= 1f) Text("✓", color = primary)
    }
}

@Composable
private fun EggikuGoalDialog(current: Int, onDismiss: () -> Unit) {
    // Suggest something realistic: the average of the last 4 weeks, rounded
    val suggested = remember {
        val log = EggikuReadingLog.all()
        val weekStart = EggikuReadingLog.weekStart()
        val total = (1..4).sumOf { back ->
            EggikuReadingLog.sum(log, weekStart.minusWeeks(back.toLong()), weekStart.minusWeeks(back - 1L).minusDays(1))
                .chapters
        }
        ((total / 4f) / 5).toInt().times(5).coerceAtLeast(5)
    }
    val options = (listOf(0, 5, 10, 20, 30, 50) + suggested).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.stats_goal_title)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                EggikuReadingLog.weeklyGoalChapters().set(option)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(
                            text = when {
                                option == 0 -> stringResource(KMR.strings.stats_goal_off)
                                option == suggested -> stringResource(KMR.strings.stats_goal_option_suggested, option)
                                else -> stringResource(KMR.strings.stats_goal_option, option)
                            },
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun EggikuStatsSettingsDialog(onDismiss: () -> Unit) {
    val weeklyRecap by EggikuReadingLog.weeklyRecap().collectPrefAsState()
    val reminder by EggikuReadingLog.nightlyReminder().collectPrefAsState()
    val dayStart by EggikuReadingLog.dayStartHour().collectPrefAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.stats_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSwitch(
                    title = stringResource(KMR.strings.stats_weekly_recap_setting),
                    checked = weeklyRecap,
                    onChange = { EggikuReadingLog.weeklyRecap().set(it) },
                )
                SettingSwitch(
                    title = stringResource(KMR.strings.stats_reminder_setting),
                    checked = reminder,
                    onChange = { EggikuReadingLog.nightlyReminder().set(it) },
                )
                Text(stringResource(KMR.strings.stats_day_starts_at), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0, 2, 4, 6).forEach { hour ->
                        val selected = hour == dayStart
                        if (selected) {
                            FilledTonalButton(onClick = {}) { Text(EggikuStatsFormat.hour(hour)) }
                        } else {
                            TextButton(onClick = { EggikuReadingLog.dayStartHour().set(hour) }) {
                                Text(EggikuStatsFormat.hour(hour))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_ok)) }
        },
    )
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
// KMK <--
