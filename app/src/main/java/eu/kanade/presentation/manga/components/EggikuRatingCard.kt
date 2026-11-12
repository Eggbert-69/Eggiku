package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
internal val EggikuStarColor = Color(0xFFFFB81C)

/** Eggiku: your own star rating on the series page. Tap the same star again to clear it. */
@Composable
fun EggikuRatingCard(mangaId: Long, modifier: Modifier = Modifier) {
    val entries by EggikuRatings.pref().collectAsState()
    val stars = remember(entries, mangaId) { EggikuRatings.parse(entries)[mangaId] ?: 0 }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(KMR.strings.eggiku_your_rating), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (stars == 0) {
                        stringResource(KMR.strings.eggiku_rating_none)
                    } else {
                        stringResource(KMR.strings.eggiku_rating_on_shelf)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { EggikuRatings.set(mangaId, if (star == stars) 0 else star) }) {
                        Icon(
                            imageVector = if (star <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "$star",
                            tint = if (star <= stars) EggikuStarColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(26.dp)
                                .eggikuPop(star <= stars, peak = 1.35f),
                        )
                    }
                }
            }
        }
    }
}
// KMK <--
