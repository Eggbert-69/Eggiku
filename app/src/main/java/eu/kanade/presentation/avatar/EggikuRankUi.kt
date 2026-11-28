package eu.kanade.presentation.avatar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuRankTree
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

private val ROMAN = listOf("", "I", "II", "III")

/** "Comet II ★2", "Fresh Egg", or the secret rank's name */
@Composable
fun eggikuRankTitle(progress: EggikuAvatarProgress, catalog: EggikuAvatarCatalog.Catalog): String {
    if (progress.glorb) return rankName(progress.rank(catalog))
    val standing = progress.standing
    val branch = standing.branch ?: return stringResource(KMR.strings.tree_fresh_egg)
    val stars = "★".repeat(standing.prestige)
    val names = EggikuRankTree.nodeNames.getValue(branch)
    val base = if (standing.node > 0) {
        stringResource(names[standing.node - 1]) + " " + ROMAN[standing.step.coerceIn(0, 3)]
    } else {
        stringResource(names.last())
    }
    return if (stars.isEmpty()) base else "$base $stars"
}

fun eggikuBranchColor(branch: EggikuRankTree.Branch?, fallback: Color): Color =
    branch?.let { Color(it.accent) } ?: fallback

/**
 * One line of where you stand (XP and points) plus a bar towards the next egg point, and a hint
 * when you need to pick or rebirth a branch.
 */
@Composable
fun EggikuRankProgress(progress: EggikuAvatarProgress, modifier: Modifier = Modifier) {
    val standing = progress.standing
    val state = standing.state
    val active = state.activeBranch
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(KMR.strings.tree_xp_points, progress.xp, standing.points),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fraction = standing.xpIntoPoint.toFloat() / EggikuRankTree.XP_PER_POINT
        val animated by animateFloatAsState(
            fraction,
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
            label = "rank",
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = eggikuBranchColor(active ?: standing.branch, MaterialTheme.colorScheme.primary),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        val hint = when {
            active == null -> stringResource(KMR.strings.tree_hint_pick)
            state.isFinished(active) -> stringResource(KMR.strings.tree_hint_finished)
            else -> stringResource(
                KMR.strings.tree_hint_next,
                EggikuRankTree.XP_PER_POINT - standing.xpIntoPoint,
            )
        }
        Text(
            text = hint + if (standing.unspent > 0) " · " + stringResource(KMR.strings.tree_unspent, standing.unspent) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
// KMK <--
