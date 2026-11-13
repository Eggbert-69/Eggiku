package eu.kanade.tachiyomi.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.avatar.EggikuAvatarImage
import eu.kanade.presentation.avatar.rankName
import eu.kanade.presentation.avatar.rememberEggikuAvatarCatalog
import eu.kanade.presentation.avatar.rememberMyEggikuAvatar
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuAvatars
import eu.kanade.tachiyomi.ui.avatar.EggikuAvatarEditorScreen
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->
/** Eggiku achievements: a ladder per stat with lots of small tiers, plus one-off and secret badges */
class EggikuAchievementsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val profileLayer = rememberGraphicsLayer()
        var comparing by remember { mutableStateOf(false) }
        val data by produceState<Pair<EggikuAchievements.Facts, Map<String, LocalDate>>?>(null) {
            value = withIOContext {
                val facts = EggikuAchievements.facts()
                EggikuAchievements.checkNew(facts)
                facts to EggikuAchievements.earned()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.ach_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (data != null) {
                            IconButton(onClick = { comparing = true }) {
                                Icon(Icons.Outlined.People, contentDescription = stringResource(KMR.strings.compare_action))
                            }
                        }
                        if (data != null) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val bitmap = profileLayer.toImageBitmap().asAndroidBitmap()
                                        share(context, bitmap)
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = stringResource(KMR.strings.recap_share))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val (facts, earned) = data ?: run {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            val earnedCount = earned.size.coerceAtMost(EggikuAchievements.totalCount)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "profile") {
                    Box(
                        modifier = Modifier.drawWithContent {
                            // Recorded so the share button can send it as an image
                            profileLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(profileLayer)
                        },
                    ) {
                        ProfileCard(facts, earnedCount)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "avatar-button") {
                    CustomizeAvatarButton { navigator.push(EggikuAvatarEditorScreen()) }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "ladders-header") {
                    Header(stringResource(KMR.strings.ach_ladders))
                }
                items(
                    EggikuAchievements.ladders,
                    key = { it.key },
                    span = { GridItemSpan(maxLineSpan) },
                ) { ladder ->
                    LadderCard(ladder, facts, earned)
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "badges-header") {
                    Header(stringResource(KMR.strings.ach_badges))
                }
                items(EggikuAchievements.oneOffs, key = { it.key }) { badge ->
                    BadgeCard(badge, earned[badge.earnedKey])
                }
            }
            if (comparing) CompareDialog(facts) { comparing = false }
        }
    }

    @Composable
    private fun CompareDialog(facts: EggikuAchievements.Facts, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val nicknamePref = remember { EggikuCompare.nickname() }
        var nickname by remember { mutableStateOf(nicknamePref.get()) }
        var pasted by remember { mutableStateOf("") }
        var bad by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(KMR.strings.compare_action)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(20) },
                        label = { Text(stringResource(KMR.strings.compare_nickname)) },
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = {
                            nicknamePref.set(nickname.trim())
                            val code = EggikuCompare.encode(EggikuCompare.mine(facts))
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, context.stringResource(KMR.strings.compare_share_text, code))
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    ) { Text(stringResource(KMR.strings.compare_share)) }
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = {
                            pasted = it
                            bad = false
                        },
                        label = { Text(stringResource(KMR.strings.compare_paste)) },
                        isError = bad,
                        supportingText = if (bad) {
                            { Text(stringResource(KMR.strings.compare_bad_code)) }
                        } else {
                            null
                        },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pasted.isNotBlank(),
                    onClick = {
                        val friend = EggikuCompare.decode(pasted)
                        if (friend == null) {
                            bad = true
                        } else {
                            nicknamePref.set(nickname.trim())
                            onDismiss()
                            navigator.push(EggikuCompareScreen(friend))
                        }
                    },
                ) { Text(stringResource(KMR.strings.compare_open)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.eggiku_close)) }
            },
        )
    }

    @Composable
    private fun CustomizeAvatarButton(onClick: () -> Unit) {
        val catalog = rememberEggikuAvatarCatalog()
        val seen by remember { EggikuAvatars.seenPref() }.collectPrefAsState()
        val hasNew = remember(seen) {
            val progress = EggikuAvatarProgress.mine()
            catalog.parts.any {
                it.unlock.type != "free" && progress.isUnlocked(catalog, it) && "${it.slot}/${it.id}" !in seen
            }
        }
        Box {
            FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Face, contentDescription = null)
                Text(
                    text = stringResource(if (hasNew) KMR.strings.avatar_customize_new else KMR.strings.avatar_customize),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (hasNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
    }

    @Composable
    private fun Header(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ProfileCard(facts: EggikuAchievements.Facts, earnedCount: Int) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val navigator = LocalNavigator.currentOrThrow
            val catalog = rememberEggikuAvatarCatalog()
            val avatar = rememberMyEggikuAvatar()
            val progress = remember(earnedCount) { EggikuAvatarProgress.mine() }
            val rank = progress.rank(catalog)
            val next = progress.nextRank(catalog)
            val nickname = remember { EggikuCompare.nickname().get() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EggikuAvatarImage(
                    avatar = avatar,
                    size = 96.dp,
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
                        text = nickname.ifBlank { stringResource(KMR.strings.ach_profile_title) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                    Text(
                        text = rankName(rank) + " · " + stringResource(KMR.strings.avatar_xp, progress.xp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
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
                        Text(
                            text = stringResource(KMR.strings.avatar_next_rank, (to - progress.xp).coerceAtLeast(0), next.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = stringResource(KMR.strings.ach_count, earnedCount, EggikuAchievements.totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EggikuAchievements.ladders.forEach { ladder ->
                    val tiers = ladder.tiersEarned(facts)
                    val metal = if (tiers > 0) ladder.metal(tiers - 1).color else scheme.outline
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(scheme.surface.copy(alpha = 0.6f))
                            .border(1.5.dp, metal, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ladder.emoji, fontSize = 14.sp)
                        Text(
                            text = " " + if (tiers > 0) {
                                stringResource(ladder.tierName(tiers - 1))
                            } else {
                                "–"
                            } + "  $tiers/${ladder.thresholds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LadderCard(
        ladder: EggikuAchievements.Ladder,
        facts: EggikuAchievements.Facts,
        earned: Map<String, LocalDate>,
    ) {
        var expanded by remember { mutableStateOf(false) }
        val value = ladder.value(facts)
        val tiers = ladder.tiersEarned(facts)
        val next = ladder.thresholds.getOrNull(tiers)
        val previousTarget = ladder.thresholds.getOrNull(tiers - 1) ?: 0L
        val scheme = MaterialTheme.colorScheme
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ladder.emoji, fontSize = 28.sp, modifier = Modifier.eggikuPop(tiers > 0, peak = 1.2f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(stringResource(ladder.title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (tiers > 0) {
                                stringResource(ladder.tierName(tiers - 1)) + " · " +
                                    stringResource(KMR.strings.ach_tier_of, tiers, ladder.thresholds.size)
                            } else {
                                stringResource(KMR.strings.ach_not_started)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tiers > 0) ladder.metal(tiers - 1).color else scheme.onSurfaceVariant,
                        )
                    }
                    Text(ladder.format(value), style = MaterialTheme.typography.titleMedium)
                }
                if (next != null) {
                    LinearProgressIndicator(
                        progress = {
                            ((value - previousTarget).toFloat() / (next - previousTarget)).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(KMR.strings.ach_next, stringResource(ladder.description, ladder.format(next))),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(KMR.strings.ach_ladder_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = EggikuAchievements.Metal.Cosmic.color,
                    )
                }
                // One dot per tier
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ladder.thresholds.indices.forEach { index ->
                        val got = index < tiers
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (got) ladder.metal(index).color else Color.Transparent)
                                .border(1.dp, ladder.metal(index).color.copy(alpha = 0.6f), CircleShape),
                        )
                    }
                }
                if (expanded) {
                    ladder.thresholds.forEachIndexed { index, target ->
                        val date = earned[ladder.tierKey(index)]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (index < tiers) 1f else 0.5f)
                                .padding(top = 4.dp),
                        ) {
                            Text(
                                text = stringResource(ladder.tierName(index)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ladder.metal(index).color,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = ladder.format(target) + (date?.let { "  ·  " + it.format(dateFormat) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BadgeCard(badge: EggikuAchievements.OneOff, date: LocalDate?) {
        val hidden = badge.secret && date == null
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (date != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (hidden) "❓" else badge.emoji,
                    fontSize = 34.sp,
                    modifier = Modifier
                        .alpha(if (date != null) 1f else 0.35f)
                        .eggikuPop(date != null, peak = 1.3f),
                )
                Text(
                    text = if (hidden) "???" else stringResource(badge.title),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (hidden) stringResource(KMR.strings.ach_secret) else stringResource(badge.description),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (date != null) {
                    Text(
                        text = date.format(dateFormat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    private fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "eggiku_recaps").apply { mkdirs() }
        val file = File(dir, "eggiku-achievements.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, file.getUriCompat(context))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
// KMK <--
