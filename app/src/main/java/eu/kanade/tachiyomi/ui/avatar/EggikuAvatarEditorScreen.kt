package eu.kanade.tachiyomi.ui.avatar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.avatar.EGGIKU_AVATAR_ASSETS
import eu.kanade.presentation.avatar.EggikuAvatarImage
import eu.kanade.presentation.avatar.eggikuColor
import eu.kanade.presentation.avatar.rankName
import eu.kanade.presentation.avatar.rememberEggikuAvatarCatalog
import eu.kanade.presentation.avatar.rememberMyEggikuAvatar
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.avatar.EggikuAvatar
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog.thumbnail
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuAvatars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Tab id for picking the rank border (not a catalog slot) */
private const val BORDER = "border"

private fun <T> jiggle() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/** Eggiku: build your avatar for the friend card. Changes save as you go. */
class EggikuAvatarEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val catalog = rememberEggikuAvatarCatalog()
        val saved = rememberMyEggikuAvatar()
        val progress = remember { EggikuAvatarProgress.mine() }
        val facts by produceState<EggikuAchievements.Facts?>(null) {
            value = withIOContext {
                try {
                    EggikuAchievements.facts()
                } catch (_: Exception) {
                    null
                }
            }
        }
        val seenPref = remember { EggikuAvatars.seenPref() }
        val seen by seenPref.collectPrefAsState()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var slot by rememberSaveable { mutableStateOf("body") }
        var unlockedOnly by rememberSaveable { mutableStateOf(false) }
        var lockedPart by remember { mutableStateOf<EggikuAvatarCatalog.Part?>(null) }
        var lockedRank by remember { mutableStateOf<EggikuAvatarCatalog.Rank?>(null) }

        fun change(avatar: EggikuAvatar) = EggikuAvatars.save(avatar)

        fun changeWithUndo(avatar: EggikuAvatar, message: StringResource, context: android.content.Context) {
            val before = saved
            change(avatar)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = context.stringResource(message),
                    actionLabel = context.stringResource(MR.strings.action_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) change(before)
            }
        }

        // Unlocked parts count as seen a moment after their tab is opened
        LaunchedEffect(slot) {
            delay(1_500)
            val keys = catalog.slot(slot).filter { progress.isUnlocked(catalog, it) }.map { "${it.slot}/${it.id}" }
            if (keys.any { it !in seenPref.get() }) seenPref.set(seenPref.get() + keys)
        }

        val context = LocalContext.current
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.avatar_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = {
                            changeWithUndo(randomAvatar(catalog, progress), KMR.strings.avatar_randomized, context)
                        }) {
                            Icon(Icons.Outlined.Casino, contentDescription = stringResource(KMR.strings.avatar_randomize))
                        }
                        IconButton(onClick = {
                            changeWithUndo(EggikuAvatar(), KMR.strings.avatar_reset_done, context)
                        }) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(MR.strings.action_reset))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                PreviewHeader(saved, progress, catalog)
                SlotTabs(
                    selected = slot,
                    hasNew = { s ->
                        catalog.slot(s).any {
                            it.unlock.type != "free" && progress.isUnlocked(catalog, it) && "${it.slot}/${it.id}" !in seen
                        }
                    },
                    onSelect = { slot = it },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(KMR.strings.avatar_unlocked_count, unlockedCount(catalog, progress), catalog.parts.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = unlockedOnly,
                        onClick = { unlockedOnly = !unlockedOnly },
                        label = { Text(stringResource(KMR.strings.avatar_unlocked_only)) },
                    )
                }
                PartGrid(
                    slot = slot,
                    avatar = saved,
                    catalog = catalog,
                    progress = progress,
                    seen = seen,
                    unlockedOnly = unlockedOnly,
                    bottomPadding = padding.calculateBottomPadding(),
                    onPick = { id -> change(saved.with(slot, id)) },
                    onPickColor = { change(saved.copy(bgColor = it)) },
                    onPickBorder = { id -> change(saved.copy(border = id)) },
                    onLocked = { lockedPart = it },
                    onLockedRank = { lockedRank = it },
                )
            }
        }

        lockedPart?.let { part ->
            LockedDialog(
                title = part.name,
                condition = conditionText(part, catalog),
                progress = progressText(part, facts),
                onDismiss = { lockedPart = null },
            )
        }
        lockedRank?.let { rank ->
            LockedDialog(
                title = if (rank.secret) "???" else rank.name,
                condition = rankCondition(rank),
                progress = stringResource(KMR.strings.avatar_xp, progress.xp),
                onDismiss = { lockedRank = null },
            )
        }
    }

    @Composable
    private fun PreviewHeader(
        avatar: EggikuAvatar,
        progress: EggikuAvatarProgress,
        catalog: EggikuAvatarCatalog.Catalog,
    ) {
        val rank = progress.border(catalog, avatar)
        val current = progress.rank(catalog)
        val next = progress.nextRank(catalog)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EggikuAvatarImage(avatar = avatar, size = 150.dp, border = rank, bounce = true)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = rankName(current),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = eggikuColor(current?.accent, MaterialTheme.colorScheme.primary),
                )
                Text(stringResource(KMR.strings.avatar_xp, progress.xp), style = MaterialTheme.typography.bodyMedium)
                if (next != null) {
                    val from = current?.xp?.takeIf { current.secret.not() } ?: 0
                    val to = next.xp ?: 1
                    val fraction = ((progress.xp - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f)
                    val animated by animateFloatAsState(fraction, jiggle(), label = "xp")
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
                        text = stringResource(KMR.strings.avatar_next_rank, (to - progress.xp).coerceAtLeast(0), next.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(KMR.strings.avatar_top_rank),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(KMR.strings.avatar_how_xp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun SlotTabs(selected: String, hasNew: (String) -> Boolean, onSelect: (String) -> Unit) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(EggikuAvatarCatalog.SLOTS + BORDER) { s ->
                val isSelected = s == selected
                val scale by animateFloatAsState(if (isSelected) 1.06f else 1f, jiggle(), label = "tab")
                Box {
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(s) },
                        label = { Text(stringResource(slotName(s))) },
                        shape = CircleShape,
                        modifier = Modifier.scale(scale),
                    )
                    if (s != BORDER && hasNew(s)) NewDot(Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }

    @Composable
    private fun PartGrid(
        slot: String,
        avatar: EggikuAvatar,
        catalog: EggikuAvatarCatalog.Catalog,
        progress: EggikuAvatarProgress,
        seen: Set<String>,
        unlockedOnly: Boolean,
        bottomPadding: androidx.compose.ui.unit.Dp,
        onPick: (String?) -> Unit,
        onPickColor: (String) -> Unit,
        onPickBorder: (String?) -> Unit,
        onLocked: (EggikuAvatarCatalog.Part) -> Unit,
        onLockedRank: (EggikuAvatarCatalog.Rank) -> Unit,
    ) {
        val parts = catalog.slot(slot).filter { !unlockedOnly || progress.isUnlocked(catalog, it) }
        val (core, tropes) = parts.partition { !it.isTrope }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (slot == BORDER) {
                item(key = "border-auto") {
                    Tile(
                        label = stringResource(KMR.strings.avatar_border_auto),
                        selected = avatar.border == null,
                        onClick = { onPickBorder(null) },
                    ) {
                        EggikuAvatarImage(avatar.copy(border = null), size = 64.dp, border = progress.rank(catalog))
                    }
                }
                val ranks = catalog.ranks.filter { !unlockedOnly || progress.hasRank(catalog, it.id) }
                items(ranks, key = { "rank-" + it.id }) { rank ->
                    val owned = progress.hasRank(catalog, rank.id)
                    Tile(
                        label = if (rank.secret && !owned) "???" else rank.name,
                        selected = avatar.border == rank.id,
                        locked = !owned,
                        onClick = { if (owned) onPickBorder(rank.id) else onLockedRank(rank) },
                    ) {
                        AsyncImage(
                            model = EGGIKU_AVATAR_ASSETS + (rank.files?.png ?: ""),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                return@LazyVerticalGrid
            }
            if (slot == "background_pattern") {
                item(key = "colors", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(catalog.backgroundColors) { hex ->
                            val selected = hex.equals(avatar.bgColor, ignoreCase = true)
                            val scale by animateFloatAsState(if (selected) 1.15f else 1f, jiggle(), label = "color")
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .background(eggikuColor(hex))
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    )
                                    .clickable { onPickColor(hex) },
                            )
                        }
                    }
                }
            }
            if (slot in EggikuAvatarCatalog.OPTIONAL) {
                item(key = "none") {
                    Tile(label = stringResource(KMR.strings.avatar_none), selected = avatar[slot] == null, onClick = { onPick(null) }) {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            items(core, key = { it.slot + it.id }) { part ->
                PartTile(part, avatar, catalog, progress, seen, onPick, onLocked)
            }
            if (tropes.isNotEmpty()) {
                item(key = "tropes", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(KMR.strings.avatar_trope_header),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(tropes, key = { it.slot + it.id }) { part ->
                    PartTile(part, avatar, catalog, progress, seen, onPick, onLocked)
                }
            }
        }
    }

    @Composable
    private fun PartTile(
        part: EggikuAvatarCatalog.Part,
        avatar: EggikuAvatar,
        catalog: EggikuAvatarCatalog.Catalog,
        progress: EggikuAvatarProgress,
        seen: Set<String>,
        onPick: (String?) -> Unit,
        onLocked: (EggikuAvatarCatalog.Part) -> Unit,
    ) {
        val unlocked = progress.isUnlocked(catalog, part)
        val isNew = unlocked && part.unlock.type != "free" && "${part.slot}/${part.id}" !in seen
        Tile(
            label = part.name,
            selected = avatar[part.slot] == part.id,
            locked = !unlocked,
            isNew = isNew,
            onClick = { if (unlocked) onPick(part.id) else onLocked(part) },
        ) {
            if (part.slot == "background_pattern") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(eggikuColor(avatar.bgColor)),
                )
            }
            part.thumbnail(avatar)?.let { path ->
                AsyncImage(
                    model = EGGIKU_AVATAR_ASSETS + path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Composable
    private fun Tile(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        locked: Boolean = false,
        isNew: Boolean = false,
        content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
    ) {
        val scale by animateFloatAsState(if (selected) 1.05f else 1f, jiggle(), label = "tile")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = if (selected) 2.5.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(onClick = onClick),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .alpha(if (locked) 0.3f else 1f),
                    content = content,
                )
                if (locked) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (isNew) NewDot(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    @Composable
    private fun NewDot(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
    }

    @Composable
    private fun LockedDialog(title: String, condition: String, progress: String?, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(condition, style = MaterialTheme.typography.bodyLarge)
                    if (progress != null) {
                        Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_ok)) } },
        )
    }

    @Composable
    private fun conditionText(part: EggikuAvatarCatalog.Part, catalog: EggikuAvatarCatalog.Catalog): String {
        val unlock = part.unlock
        return when (unlock.type) {
            "ladder" -> {
                val ladder = EggikuAchievements.ladders.firstOrNull { it.key == unlock.ladder }
                val target = ladder?.thresholds?.getOrNull(unlock.tier ?: 0)
                if (ladder != null && target != null) {
                    stringResource(ladder.description, ladder.format(target))
                } else {
                    unlock.condition.orEmpty()
                }
            }
            "badge" -> {
                val badge = EggikuAchievements.oneOffs.firstOrNull { it.key == unlock.key }
                when {
                    badge == null -> unlock.condition.orEmpty()
                    badge.secret -> stringResource(KMR.strings.avatar_secret_badge)
                    else -> stringResource(KMR.strings.avatar_earn_badge, stringResource(badge.title)) +
                        "\n" + stringResource(badge.description)
                }
            }
            "rank" -> catalog.rank(unlock.rank)?.let { rankCondition(it) } ?: unlock.condition.orEmpty()
            else -> ""
        }
    }

    @Composable
    private fun rankCondition(rank: EggikuAvatarCatalog.Rank): String = if (rank.secret) {
        stringResource(KMR.strings.avatar_secret_rank)
    } else {
        stringResource(KMR.strings.avatar_reach_rank, rank.name, rank.xp ?: 0)
    }

    @Composable
    private fun progressText(part: EggikuAvatarCatalog.Part, facts: EggikuAchievements.Facts?): String? {
        if (part.unlock.type != "ladder" || facts == null) return null
        val ladder = EggikuAchievements.ladders.firstOrNull { it.key == part.unlock.ladder } ?: return null
        val target = ladder.thresholds.getOrNull(part.unlock.tier ?: 0) ?: return null
        val value = ladder.value(facts).coerceAtMost(target)
        return stringResource(KMR.strings.avatar_progress, ladder.format(value), ladder.format(target))
    }

    private fun unlockedCount(catalog: EggikuAvatarCatalog.Catalog, progress: EggikuAvatarProgress) =
        catalog.parts.count { progress.isUnlocked(catalog, it) }

    private fun randomAvatar(catalog: EggikuAvatarCatalog.Catalog, progress: EggikuAvatarProgress): EggikuAvatar {
        val always = setOf("body", "eyes", "mouth", "background_pattern")
        val often = setOf("cheeks", "hat")
        var avatar = EggikuAvatar(bgColor = catalog.backgroundColors.randomOrNull() ?: "#FFE3C2")
        EggikuAvatarCatalog.SLOTS.forEach { slot ->
            val choices = catalog.slot(slot).filter { progress.isUnlocked(catalog, it) }
            val chance = when (slot) {
                in always -> 1f
                in often -> 0.7f
                else -> 0.35f
            }
            val pick = if (Math.random() < chance) choices.randomOrNull()?.id else null
            avatar = avatar.with(slot, pick)
        }
        return avatar
    }

    private fun slotName(slot: String): StringResource = when (slot) {
        "body" -> KMR.strings.avatar_slot_body
        "pattern" -> KMR.strings.avatar_slot_pattern
        "antenna" -> KMR.strings.avatar_slot_antenna
        "eyes" -> KMR.strings.avatar_slot_eyes
        "mouth" -> KMR.strings.avatar_slot_mouth
        "cheeks" -> KMR.strings.avatar_slot_cheeks
        "hat" -> KMR.strings.avatar_slot_hat
        "face" -> KMR.strings.avatar_slot_face
        "neck" -> KMR.strings.avatar_slot_neck
        "ear" -> KMR.strings.avatar_slot_ear
        "back" -> KMR.strings.avatar_slot_back
        "held" -> KMR.strings.avatar_slot_held
        "front_effect" -> KMR.strings.avatar_slot_front_effect
        "back_effect" -> KMR.strings.avatar_slot_back_effect
        "background_pattern" -> KMR.strings.avatar_slot_background
        else -> KMR.strings.avatar_slot_border
    }
}
// KMK <--
