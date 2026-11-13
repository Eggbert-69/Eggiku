package eu.kanade.presentation.more.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.components.drawEggikuHatched
import eu.kanade.presentation.components.eggikuEggColor
import eu.kanade.tachiyomi.data.stats.EggikuEggCarton
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuReadingSpeed
import eu.kanade.tachiyomi.ui.stats.EggikuAchievementsScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Snappy, with a small overshoot so you can see when it has settled */
private fun <T> jiggle() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/** Everything the Eggiku stats cards need, read once for the whole page */
@Immutable
class EggikuReadingData(
    val log: Map<LocalDate, EggikuReadingLog.DayStats>,
    val today: LocalDate,
    val streak: EggikuReadingLog.Streak,
    val week: EggikuReadingLog.DayStats,
    /** Last week up to the same weekday, so early in the week isn't always "down" */
    val lastWeekSoFar: EggikuReadingLog.DayStats,
    val allTime: EggikuReadingLog.DayStats,
    val goal: Int,
)

@Composable
fun rememberEggikuReadingData(): EggikuReadingData {
    val version by EggikuReadingLog.changes.collectAsState()
    val goal by EggikuReadingLog.weeklyGoalChapters().collectPrefAsState()
    val dayStart by EggikuReadingLog.dayStartHour().collectPrefAsState()
    return remember(version, dayStart, goal) {
        val log = EggikuReadingLog.all()
        val today = EggikuReadingLog.today()
        val weekStart = EggikuReadingLog.weekStart(today)
        EggikuReadingData(
            log = log,
            today = today,
            streak = EggikuReadingLog.streak(log, today),
            week = EggikuReadingLog.sum(log, weekStart, today),
            lastWeekSoFar = EggikuReadingLog.sum(log, weekStart.minusWeeks(1), today.minusWeeks(1)),
            allTime = EggikuReadingLog.sum(log, LocalDate.MIN, LocalDate.MAX),
            goal = goal,
        )
    }
}

/** Shortcuts, streak and this week: the top of Stats */
fun LazyListScope.eggikuReadingTopItems(data: EggikuReadingData, onOpenRecaps: () -> Unit) {
    item(key = "eggiku_shortcuts") { StatsShortcuts(onOpenRecaps = onOpenRecaps) }
    item(key = "eggiku_streak") {
        SectionCard(KMR.strings.stats_streak_title) { StreakCard(data) }
    }
    item(key = "eggiku_week") {
        SectionCard(KMR.strings.stats_this_week) { WeekCard(data) }
    }
    item(key = "eggiku_carton") {
        SectionCard(KMR.strings.egg_carton_title) { EggCartonCard() }
    }
}

// region Egg carton

/** This week's hatched egg timer sessions, in a 12-egg carton */
@Composable
private fun EggCartonCard() {
    val flow = remember { EggikuEggCarton.changes() }
    val eggs by flow.collectAsState(initial = remember { EggikuEggCarton.all() })
    val week = remember(eggs) { EggikuEggCarton.thisWeek(eggs) }
    val cup = MaterialTheme.colorScheme.surfaceContainerHighest
    val carton = MaterialTheme.colorScheme.secondaryContainer
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(carton, RoundedCornerShape(18.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until 6).forEach { column ->
                            val egg = week.getOrNull(row * 6 + column)
                            val scale by animateFloatAsState(if (egg != null) 1f else 0.9f, jiggle(), label = "cup")
                            Canvas(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .scale(scale),
                            ) {
                                drawCircle(cup, radius = size.minDimension / 2f)
                                if (egg != null) {
                                    val h = size.height * 0.92f
                                    val w = h * 36f / 46f
                                    drawEggikuHatched(
                                        topLeft = Offset((size.width - w) / 2f, size.height - h * 1.02f),
                                        size = Size(w, h),
                                        shell = eggikuEggColor(egg.minutes),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val minutes = week.sumOf { it.minutes }
        Text(
            text = if (week.isEmpty()) {
                stringResource(KMR.strings.egg_carton_empty)
            } else {
                pluralStringResource(KMR.plurals.egg_carton_week, week.size, week.size, minutes) +
                    if (week.size > 12) " " + stringResource(KMR.strings.egg_carton_overflow, week.size - 12) else ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (eggs.isNotEmpty()) {
            Text(
                text = pluralStringResource(KMR.plurals.egg_carton_all_time, eggs.size, eggs.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// endregion

/** Reader habits and genres, further down the page */
fun LazyListScope.eggikuReadingHabitItems(data: EggikuReadingData) {
    item(key = "eggiku_habits") {
        SectionCard(KMR.strings.stats_reading_habits) { HabitsCard(data.allTime) }
    }
    item(key = "eggiku_genres") { EggikuGenreBreakdown() }
}

// region Shortcuts

@Composable
private fun StatsShortcuts(onOpenRecaps: () -> Unit) {
    val navigator = LocalNavigator.current
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) EggikuStatsSettingsDialog(onDismiss = { showSettings = false })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        ShortcutTile(Icons.Outlined.History, stringResource(KMR.strings.stats_recaps), onOpenRecaps)
        ShortcutTile(Icons.Outlined.EmojiEvents, stringResource(KMR.strings.ach_title)) {
            navigator?.push(EggikuAchievementsScreen())
        }
        ShortcutTile(Icons.Outlined.Settings, stringResource(MR.strings.action_settings)) { showSettings = true }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ShortcutTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

// endregion

// region Streak

@Composable
private fun StreakCard(data: EggikuReadingData) {
    val streak = data.streak
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 34.sp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = pluralStringResource(KMR.plurals.stats_streak_days, streak.current, streak.current),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        when {
                            streak.readToday -> KMR.strings.stats_streak_done_today
                            streak.current > 0 -> KMR.strings.stats_streak_keep_going
                            else -> KMR.strings.stats_streak_start
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (streak.current > 0 && !streak.readToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallPill(pluralStringResource(KMR.plurals.stats_best_streak, streak.longest, streak.longest))
                SmallPill("❄️ " + pluralStringResource(KMR.plurals.stats_freezes_saved, streak.freezes, streak.freezes))
            }
        }

        LastSevenDays(log = data.log, today = data.today)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCalendar = !showCalendar }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(if (showCalendar) KMR.strings.stats_hide_calendar else KMR.strings.stats_show_calendar),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(
            visible = showCalendar,
            enter = expandVertically(jiggle()) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactHeatmap(log = data.log, today = data.today)
                Text(
                    text = stringResource(KMR.strings.stats_streak_today_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One dot per day for the last week, today on the right */
@Composable
private fun LastSevenDays(log: Map<LocalDate, EggikuReadingLog.DayStats>, today: LocalDate) {
    val locale = Locale.getDefault()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val counted = log[day]?.counts == true
            val isToday = back == 0
            val scale by animateFloatAsState(if (counted) 1f else 0.86f, jiggle(), label = "day")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(scale)
                        .background(
                            if (counted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .then(
                            if (isToday && !counted) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (counted) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Small fixed-size squares, as many weeks as fit the width (up to half a year) */
@Composable
private fun CompactHeatmap(log: Map<LocalDate, EggikuReadingLog.DayStats>, today: LocalDate) {
    val cell = 12.dp
    val gap = 3.dp
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val weeks = ((maxWidth + gap) / (cell + gap)).toInt().coerceIn(8, 26)
        val start = EggikuReadingLog.weekStart(today).minusWeeks((weeks - 1).toLong())
        val levels = remember(log, today, weeks) {
            (0 until weeks * 7).map { offset ->
                val day = start.plusDays(offset.toLong())
                if (day.isAfter(today)) -1 else heatLevel(log[day])
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(
                modifier = Modifier
                    .width(cell * weeks + gap * (weeks - 1))
                    .height(cell * 7 + gap * 6),
            ) {
                val c = cell.toPx()
                val g = gap.toPx()
                levels.forEachIndexed { index, level ->
                    if (level < 0) return@forEachIndexed
                    drawRoundRect(
                        color = heatColor(level, primary, empty),
                        topLeft = Offset((index / 7) * (c + g), (index % 7) * (c + g)),
                        size = Size(c, c),
                        cornerRadius = CornerRadius(c / 3.5f),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(KMR.plurals.stats_last_weeks, weeks, weeks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(KMR.strings.stats_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (0..4).forEach { level ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(10.dp)
                            .background(heatColor(level, primary, empty), RoundedCornerShape(3.dp)),
                    )
                }
                Text(
                    stringResource(KMR.strings.stats_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 0 = nothing, then under 15 min, under 45 min, under 90 min, more */
private fun heatLevel(day: EggikuReadingLog.DayStats?): Int {
    if (day == null || (day.seconds <= 0 && day.chapters <= 0)) return 0
    val minutes = day.seconds / 60
    return when {
        minutes < 15 -> 1
        minutes < 45 -> 2
        minutes < 90 -> 3
        else -> 4
    }
}

private fun heatColor(level: Int, primary: Color, empty: Color): Color = when (level) {
    0 -> empty
    1 -> primary.copy(alpha = 0.3f)
    2 -> primary.copy(alpha = 0.55f)
    3 -> primary.copy(alpha = 0.8f)
    else -> primary
}

// endregion

// region This week

@Composable
private fun WeekCard(data: EggikuReadingData) {
    var showGoal by remember { mutableStateOf(false) }
    if (showGoal) EggikuGoalDialog(current = data.goal, onDismiss = { showGoal = false })

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Weekly goal
        if (data.goal > 0) {
            val progress = data.week.chapters / data.goal.toFloat()
            val animated by animateFloatAsState(progress.coerceIn(0f, 1f), jiggle(), label = "goal")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGoal = true },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(KMR.strings.stats_goal_progress, data.week.chapters, data.goal),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (progress >= 1f) "✓" else "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    text = stringResource(KMR.strings.stats_goal_tap_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(KMR.strings.stats_goal_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { showGoal = true }) {
                    Text(stringResource(KMR.strings.stats_goal_set))
                }
            }
        }

        HorizontalDivider()

        // This week's numbers, compared with this time last week
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekTile(
                value = "${data.week.chapters}",
                label = stringResource(KMR.strings.stats_chapters),
                delta = (data.week.chapters - data.lastWeekSoFar.chapters).toLong(),
                deltaText = { "${kotlin.math.abs(it)}" },
            )
            WeekTile(
                value = durationTextPlain(data.week.seconds),
                label = stringResource(KMR.strings.stats_time),
                delta = (data.week.seconds - data.lastWeekSoFar.seconds) / 60,
                deltaText = { minutes -> durationTextPlain(kotlin.math.abs(minutes) * 60) },
            )
            WeekTile(
                value = EggikuStatsFormat.distance(data.week.swipeMm),
                label = stringResource(KMR.strings.stats_swiped),
                delta = null,
                deltaText = { "" },
            )
        }
        Text(
            text = stringResource(KMR.strings.stats_vs_last_week_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WeekTile(
    value: String,
    label: String,
    delta: Long?,
    deltaText: @Composable (Long) -> String,
) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (delta != null) {
            Text(
                text = when {
                    delta > 0 -> "▲ " + deltaText(delta)
                    delta < 0 -> "▼ " + deltaText(delta)
                    else -> "–"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun durationTextPlain(seconds: Long): String {
    val (h, m) = EggikuStatsFormat.hoursAndMinutes(seconds)
    return if (h > 0) {
        stringResource(KMR.strings.stats_duration, h, m)
    } else {
        stringResource(KMR.strings.stats_minutes, m)
    }
}

// endregion

// region Habits

@Composable
private fun HabitsCard(allTime: EggikuReadingLog.DayStats) {
    Column {
        HabitRow(stringResource(KMR.strings.stats_swiped), EggikuStatsFormat.distance(allTime.swipeMm))
        HabitRow(stringResource(KMR.strings.stats_panned), EggikuStatsFormat.distance(allTime.panMm))
        HabitRow(stringResource(KMR.strings.stats_zooms), "%,d".format(Locale.getDefault(), allTime.zooms))
        HabitRow(stringResource(KMR.strings.stats_taps), "%,d".format(Locale.getDefault(), allTime.taps))
        EggikuReadingSpeed.pagesPerMinute()?.let { ppm ->
            HabitRow(
                stringResource(KMR.strings.stats_reading_speed),
                stringResource(KMR.strings.stats_pages_per_minute, String.format(Locale.getDefault(), "%.1f", ppm)),
            )
        }
        PaddingLine(allTime.padding)
    }
}

@Composable
private fun HabitRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PaddingLine(padding: Map<String, Long>) {
    val total = padding.values.sum()
    if (total <= 0) return
    val unfolded = padding.filterKeys { it.startsWith("unfolded") }.values.sum()
    val topUnfolded = padding.filterKeys { it.startsWith("unfolded") }.maxByOrNull { it.value }?.key
        ?.substringAfter(':')
    Spacer(Modifier.height(4.dp))
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

// endregion

// region Dialogs

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

// endregion
// KMK <--
