package eu.kanade.tachiyomi.ui.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.avatar.EGGIKU_AVATAR_ASSETS
import eu.kanade.presentation.avatar.EggikuAvatarImage
import eu.kanade.presentation.avatar.EggikuRankProgress
import eu.kanade.presentation.avatar.eggikuRankTitle
import eu.kanade.presentation.avatar.rememberEggikuAvatarCatalog
import eu.kanade.presentation.avatar.rememberMyEggikuAvatar
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.avatar.EggikuAvatar
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog.thumbnail
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuRankTree
import eu.kanade.tachiyomi.data.avatar.EggikuRankTree.Branch
import eu.kanade.tachiyomi.ui.avatar.EggikuAvatarEditorScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

/**
 * Eggiku rank tree: six branches of ten ranks. Points from XP go into the branch you're on, and
 * each rank unlocks an avatar reward. Finish a branch to rebirth it for a prestige star.
 */
class EggikuRankTreeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val catalog = rememberEggikuAvatarCatalog()
        val avatar = rememberMyEggikuAvatar()
        // Bumped after every change so the progress is worked out again
        var version by remember { mutableIntStateOf(0) }
        val progress = remember(version) { EggikuAvatarProgress.mine() }
        val state = progress.treeState
        var confirmChoose by remember { mutableStateOf<Branch?>(null) }
        var confirmRebirth by remember { mutableStateOf<Branch?>(null) }
        var nodeInfo by remember { mutableStateOf<Pair<Branch, Int>?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.tree_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            EggikuAvatarImage(
                                avatar = avatar,
                                size = 88.dp,
                                border = progress.border(catalog, avatar),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { navigator.push(EggikuAvatarEditorScreen()) },
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = eggikuRankTitle(progress, catalog),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                EggikuRankProgress(progress)
                            }
                        }
                    }
                }
                item(key = "how") {
                    Text(
                        text = stringResource(KMR.strings.tree_how, EggikuRankTree.XP_PER_POINT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(Branch.entries, key = { it.key }) { branch ->
                    BranchCard(
                        branch = branch,
                        state = state,
                        catalog = catalog,
                        onChoose = { confirmChoose = branch },
                        onRebirth = { confirmRebirth = branch },
                        onNode = { nodeInfo = branch to it },
                    )
                }
            }
        }

        confirmChoose?.let { branch ->
            AlertDialog(
                onDismissRequest = { confirmChoose = null },
                title = { Text(stringResource(KMR.strings.tree_choose_title, stringResource(branch.title))) },
                text = { Text(stringResource(KMR.strings.tree_choose_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            EggikuRankTree.choose(branch, progress.xp)
                            confirmChoose = null
                            version++
                        },
                    ) { Text(stringResource(KMR.strings.tree_start)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmChoose = null }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
        confirmRebirth?.let { branch ->
            AlertDialog(
                onDismissRequest = { confirmRebirth = null },
                title = { Text(stringResource(KMR.strings.tree_rebirth_title, stringResource(branch.title))) },
                text = { Text(stringResource(KMR.strings.tree_rebirth_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            EggikuRankTree.rebirth(branch, progress.xp)
                            confirmRebirth = null
                            version++
                        },
                    ) { Text(stringResource(KMR.strings.tree_rebirth)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRebirth = null }) { Text(stringResource(MR.strings.action_cancel)) }
                },
            )
        }
        nodeInfo?.let { (branch, node) ->
            val parts = rewardsFor(catalog, branch, node = node)
            AlertDialog(
                onDismissRequest = { nodeInfo = null },
                title = { Text(stringResource(EggikuRankTree.nodeNames.getValue(branch)[node - 1])) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(KMR.strings.tree_node_info, node, EggikuRankTree.STEPS))
                        parts.forEach { part -> RewardRow(part, avatar = avatar) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { nodeInfo = null }) { Text(stringResource(MR.strings.action_ok)) }
                },
            )
        }
    }

    @Composable
    private fun BranchCard(
        branch: Branch,
        state: EggikuRankTree.State,
        catalog: EggikuAvatarCatalog.Catalog,
        onChoose: () -> Unit,
        onRebirth: () -> Unit,
        onNode: (Int) -> Unit,
    ) {
        val accent = Color(branch.accent)
        val active = state.activeBranch == branch
        val steps = state.steps(branch)
        val prestige = state.prestige(branch)
        val reached = (steps + EggikuRankTree.STEPS - 1) / EggikuRankTree.STEPS
        val finished = state.isFinished(branch)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(if (active) Modifier.border(2.dp, accent, RoundedCornerShape(20.dp)) else Modifier),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(branch.emoji, fontSize = 22.sp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            text = stringResource(branch.title) + if (prestige > 0) " " + "★".repeat(prestige) else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when {
                                active && finished -> stringResource(KMR.strings.tree_status_finished)
                                active -> stringResource(KMR.strings.tree_status_active, steps, EggikuRankTree.STEPS_PER_BRANCH)
                                steps > 0 -> stringResource(KMR.strings.tree_status_paused, steps, EggikuRankTree.STEPS_PER_BRANCH)
                                else -> stringResource(KMR.strings.tree_status_new)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val fraction by animateFloatAsState(steps.toFloat() / EggikuRankTree.STEPS_PER_BRANCH, label = "branch")
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = accent,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..EggikuRankTree.NODES).toList()) { node ->
                        val done = node <= reached || (prestige > 0 && steps == 0)
                        val current = active && node == reached && !finished
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNode(node) }
                                .padding(vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (done) accent else MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                val reward = rewardsFor(catalog, branch, node = node).firstOrNull()
                                val path = reward?.thumbnail(EggikuAvatar())
                                if (path != null) {
                                    AsyncImage(
                                        model = EGGIKU_AVATAR_ASSETS + path,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .alpha(if (done) 1f else 0.35f),
                                    )
                                } else {
                                    Text(node.toString(), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Text(
                                text = stringResource(EggikuRankTree.nodeNames.getValue(branch)[node - 1]),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                // Prestige rewards
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(KMR.strings.tree_prestige_rewards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (1..EggikuRankTree.MAX_PRESTIGE).forEach { level ->
                        val part = rewardsFor(catalog, branch, prestige = level).firstOrNull()
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            part?.thumbnail(EggikuAvatar())?.let { path ->
                                AsyncImage(
                                    model = EGGIKU_AVATAR_ASSETS + path,
                                    contentDescription = part.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(if (prestige >= level) 1f else 0.3f),
                                )
                            }
                            if (prestige < level) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                when {
                    active && finished && prestige < EggikuRankTree.MAX_PRESTIGE -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onRebirth, modifier = Modifier.weight(1f)) {
                            Text(stringResource(KMR.strings.tree_rebirth))
                        }
                    }
                    !active && state.canSwitch() -> FilledTonalButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (steps > 0 || prestige > 0) KMR.strings.tree_continue else KMR.strings.tree_start,
                            ),
                        )
                    }
                    !active -> Text(
                        text = stringResource(KMR.strings.tree_locked_until),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    private fun RewardRow(part: EggikuAvatarCatalog.Part, avatar: EggikuAvatar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                part.thumbnail(avatar)?.let { path ->
                    AsyncImage(model = EGGIKU_AVATAR_ASSETS + path, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Text(part.name, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }

    private fun rewardsFor(
        catalog: EggikuAvatarCatalog.Catalog,
        branch: Branch,
        node: Int? = null,
        prestige: Int? = null,
    ): List<EggikuAvatarCatalog.Part> = catalog.parts.filter {
        it.unlock.branch == branch.key &&
            (node == null || (it.unlock.type == "tree" && it.unlock.node == node)) &&
            (prestige == null || (it.unlock.type == "prestige" && it.unlock.level == prestige))
    }
}
// KMK <--
