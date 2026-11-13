package eu.kanade.presentation.more.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.stats.EggikuGenres
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.time.ZoneId

// KMK -->
/** Eggiku: your most-read genres, all time, and which ones grew or shrank this year */
@Composable
fun LazyItemScope.EggikuGenreBreakdown() {
    val data by produceState<Triple<List<EggikuGenres.Share>, List<EggikuGenres.Share>, List<EggikuGenres.Share>>?>(null) {
        value = withIOContext {
            try {
                val zone = ZoneId.systemDefault()
                val yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val lastYearStart = LocalDate.now().withDayOfYear(1).minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
                Triple(
                    EggikuGenres.shares(),
                    EggikuGenres.shares(from = yearStart, limit = 30),
                    EggikuGenres.shares(from = lastYearStart, to = yearStart - 1, limit = 30),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
    val (allTime, thisYear, lastYear) = data ?: return
    if (allTime.isEmpty()) return

    SectionCard(KMR.strings.genres_title) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            allTime.forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = share.genre,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp),
                    )
                    LinearProgressIndicator(
                        progress = { share.percent / (allTime.first().percent.coerceAtLeast(1f)) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text("${share.percent.toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
            // Biggest change between last year and this year
            if (thisYear.isNotEmpty() && lastYear.isNotEmpty()) {
                val before = lastYear.associate { it.genre to it.percent }
                val shift = thisYear.map { it.genre to it.percent - (before[it.genre] ?: 0f) }
                val up = shift.maxByOrNull { it.second }?.takeIf { it.second >= 3f }
                val down = shift.minByOrNull { it.second }?.takeIf { it.second <= -3f }
                if (up != null) {
                    Text(
                        "📈 " + stringResource(KMR.strings.genres_up, up.first, up.second.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (down != null) {
                    Text(
                        "📉 " + stringResource(KMR.strings.genres_down, down.first, -down.second.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
// KMK <--
