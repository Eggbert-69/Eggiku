package eu.kanade.presentation.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuAvatars
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Eggiku: your avatar, rank and XP right in the More tab. Tap to customize. */
@Composable
fun EggikuAvatarMoreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val catalog = rememberEggikuAvatarCatalog()
    val avatar = rememberMyEggikuAvatar()
    val earned by remember { EggikuAchievements.earnedPref() }.collectPrefAsState()
    val seen by remember { EggikuAvatars.seenPref() }.collectPrefAsState()
    val nickname by remember { EggikuCompare.nickname() }.collectPrefAsState()
    val progress = remember(earned) { EggikuAvatarProgress.mine() }
    val hasNew = remember(progress, seen) {
        catalog.parts.any {
            it.unlock.type != "free" && progress.isUnlocked(catalog, it) && "${it.slot}/${it.id}" !in seen
        }
    }
    val rank = progress.rank(catalog)
    val next = progress.nextRank(catalog)

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                EggikuAvatarImage(avatar = avatar, size = 76.dp, border = progress.border(catalog, avatar))
                if (hasNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = nickname.ifBlank { stringResource(KMR.strings.avatar_title) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = rankName(rank) + " · " + stringResource(KMR.strings.avatar_xp, progress.xp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (next != null) {
                    val from = rank?.takeIf { !it.secret }?.xp ?: 0
                    val to = next.xp ?: 1
                    val fraction = ((progress.xp - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                Text(
                    text = stringResource(if (hasNew) KMR.strings.avatar_customize_new else KMR.strings.avatar_customize),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}
// KMK <--
