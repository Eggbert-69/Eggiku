package eu.kanade.presentation.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->
/** Eggiku: achievements right on the More tab, with the newest one and how many are earned */
@Composable
fun EggikuAchievementsMoreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val earnedKeys by remember { EggikuAchievements.earnedPref() }.collectPrefAsState()
    val earned = remember(earnedKeys) { EggikuAchievements.earned() }
    val total = EggikuAchievements.totalCount
    val count = earned.size.coerceAtMost(total)
    val newest = remember(earned) { earned.maxByOrNull { it.value }?.key }
    val newestLabel = newest?.let { key ->
        when {
            key.startsWith("O:") -> EggikuAchievements.oneOffs.firstOrNull { it.earnedKey == key }?.let {
                it.emoji + " " + stringResource(it.title)
            }
            key.startsWith("L:") -> {
                val ladderKey = key.substringAfter("L:").substringBeforeLast(':')
                val index = key.substringAfterLast(':').toIntOrNull()
                EggikuAchievements.ladders.firstOrNull { it.key == ladderKey }?.let { ladder ->
                    ladder.emoji + " " + stringResource(ladder.title) +
                        (index?.let { ": " + stringResource(ladder.tierName(it)) } ?: "")
                }
            }
            else -> null
        }
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Text("🏆", fontSize = 18.sp)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = stringResource(KMR.strings.ach_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = newestLabel?.let { stringResource(KMR.strings.ach_more_newest, it) }
                            ?: stringResource(KMR.strings.ach_more_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "$count / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else count.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
// KMK <--
