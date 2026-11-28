package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.util.toDurationString
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetReadDurationPerManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.milliseconds

// KMK -->
/** Eggiku: how long you've spent reading a series, rereads and migrated sources included */
private data class ReadTime(
    val totalMs: Long,
    val chapters: Int,
    val longestMs: Long,
    val lastReadAt: Long,
)

@Composable
fun EggikuReadTimeCard(mangaId: Long, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Reloads whenever the screen comes back, e.g. after closing the reader
    val readTime by produceState<ReadTime?>(null, mangaId, lifecycle) {
        lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) value = withIOContext { loadReadTime(mangaId) }
        }
    }
    val data = readTime ?: return
    if (data.totalMs < 1_000) return

    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val fallback = stringResource(KMR.strings.eggiku_read_time_none)
    fun format(ms: Long) = ms.milliseconds.toDurationString(context, fallback)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⏱ " + stringResource(KMR.strings.eggiku_read_time_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = format(data.totalMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (open) {
                val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
                ReadTimeRow(stringResource(KMR.strings.eggiku_read_time_chapters), data.chapters.toString())
                if (data.chapters > 0) {
                    ReadTimeRow(
                        stringResource(KMR.strings.eggiku_read_time_average),
                        format(data.totalMs / data.chapters),
                    )
                    ReadTimeRow(stringResource(KMR.strings.eggiku_read_time_longest), format(data.longestMs))
                }
                if (data.lastReadAt > 0) {
                    ReadTimeRow(
                        stringResource(KMR.strings.eggiku_read_time_last),
                        Instant.ofEpochMilli(data.lastReadAt).atZone(ZoneId.systemDefault()).format(dateFormat),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadTimeRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private suspend fun loadReadTime(mangaId: Long): ReadTime {
    val history = Injekt.get<GetHistory>().await(mangaId)
    val timed = history.filter { it.readDuration > 0 }
    return ReadTime(
        totalMs = timed.sumOf { it.readDuration },
        chapters = timed.size,
        longestMs = timed.maxOfOrNull { it.readDuration } ?: 0L,
        lastReadAt = history.maxOfOrNull { it.readAt?.time ?: 0L } ?: 0L,
    )
}

/** Milliseconds read per chapter of a series, keyed by chapter id */
@Composable
fun rememberEggikuChapterReadTimes(mangaId: Long): Map<Long, Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val times by produceState(emptyMap<Long, Long>(), mangaId, lifecycle) {
        lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                value = withIOContext {
                    Injekt.get<GetHistory>().await(mangaId)
                        .filter { it.readDuration >= 1_000 }
                        .associate { it.chapterId to it.readDuration }
                }
            }
        }
    }
    return times
}

/** Milliseconds read per series, keyed by manga id; reloads when [refreshKey] changes */
@Composable
fun rememberEggikuSeriesReadTimes(refreshKey: Any?): Map<Long, Long> {
    val times by produceState(emptyMap<Long, Long>(), refreshKey) {
        value = withIOContext { Injekt.get<GetReadDurationPerManga>().await() }
    }
    return times
}
// KMK <--
