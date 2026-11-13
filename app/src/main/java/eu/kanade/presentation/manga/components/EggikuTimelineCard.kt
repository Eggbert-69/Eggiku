package eu.kanade.presentation.manga.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

// KMK -->
/** Eggiku: when you started a series, binged it, took breaks and finished it */
private sealed interface Moment {
    val date: LocalDate

    data class Started(override val date: LocalDate) : Moment
    data class Binge(override val date: LocalDate, val chapters: Int) : Moment
    data class Break(override val date: LocalDate, val days: Long) : Moment
    data class CaughtUp(override val date: LocalDate) : Moment
    data class LastRead(override val date: LocalDate) : Moment
}

private const val BINGE_CHAPTERS = 10
private const val BREAK_DAYS = 30L
private const val MAX_BINGES = 4

@Composable
fun EggikuTimelineCard(mangaId: Long, modifier: Modifier = Modifier) {
    val moments by produceState<List<Moment>>(emptyList(), mangaId) {
        value = withIOContext { buildTimeline(mangaId) }
    }
    if (moments.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

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
                    text = stringResource(KMR.strings.timeline_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            val shown = if (open) moments else listOfNotNull(moments.firstOrNull(), moments.lastOrNull()).distinct()
            shown.forEach { moment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = when (moment) {
                                is Moment.Started -> "🥚 " + stringResource(KMR.strings.timeline_started)
                                is Moment.Binge -> "🍳 " + pluralStringResource(
                                    KMR.plurals.timeline_binge,
                                    moment.chapters,
                                    moment.chapters,
                                )
                                is Moment.Break -> "💤 " + pluralStringResource(
                                    KMR.plurals.timeline_break,
                                    moment.days.toInt(),
                                    moment.days.toInt(),
                                )
                                is Moment.CaughtUp -> "🏁 " + stringResource(KMR.strings.timeline_caught_up)
                                is Moment.LastRead -> "📖 " + stringResource(KMR.strings.timeline_last_read)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = moment.date.format(dateFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun buildTimeline(mangaId: Long): List<Moment> {
    val zone = ZoneId.systemDefault()
    val reads = Injekt.get<GetHistory>().await(mangaId)
        .mapNotNull { it.readAt?.time?.takeIf { t -> t > 0 } }
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        .sorted()
    if (reads.isEmpty()) return emptyList()
    val perDay = reads.groupingBy { it }.eachCount().toSortedMap()
    val moments = mutableListOf<Moment>(Moment.Started(reads.first()))

    perDay.entries
        .filter { it.value >= BINGE_CHAPTERS }
        .sortedByDescending { it.value }
        .take(MAX_BINGES)
        .forEach { moments += Moment.Binge(it.key, it.value) }

    perDay.keys.zipWithNext().forEach { (a, b) ->
        val gap = ChronoUnit.DAYS.between(a, b)
        if (gap >= BREAK_DAYS) moments += Moment.Break(a, gap)
    }

    val chapters = Injekt.get<GetChaptersByMangaId>().await(mangaId)
    if (chapters.isNotEmpty() && chapters.all { it.read }) {
        moments += Moment.CaughtUp(reads.last())
    } else if (reads.last() != reads.first()) {
        moments += Moment.LastRead(reads.last())
    }
    return moments.sortedBy { it.date }
}
// KMK <--
