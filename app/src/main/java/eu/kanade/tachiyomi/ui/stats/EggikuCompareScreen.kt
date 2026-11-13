package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.achievements.EggikuCompare
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

// KMK -->
/** Eggiku: your achievements next to a friend's, ladder by ladder */
class EggikuCompareScreen(private val friend: EggikuCompare.Profile) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val me by produceState<EggikuCompare.Profile?>(null) {
            value = withIOContext {
                val facts = EggikuAchievements.facts()
                EggikuAchievements.checkNew(facts)
                EggikuCompare.mine(facts)
            }
        }
        val friendName = friend.name.ifBlank { stringResource(KMR.strings.compare_friend) }
        val myName = me?.name?.ifBlank { null } ?: stringResource(KMR.strings.compare_you)

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.compare_title, friendName),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val mine = me ?: run {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Header(myName, mine.earned, mine.total, Modifier.weight(1f))
                        Header(friendName, friend.earned, friend.total, Modifier.weight(1f))
                    }
                }
                items(EggikuAchievements.ladders, key = { it.key }) { ladder ->
                    val a = mine.tiers[ladder.key] ?: 0
                    val b = friend.tiers[ladder.key] ?: 0
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${ladder.emoji} " + stringResource(ladder.title), style = MaterialTheme.typography.titleSmall)
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Side(ladder, a, leading = a > b, Modifier.weight(1f))
                                Text("vs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Side(ladder, b, leading = b > a, Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    val shared = mine.badges.intersect(friend.badges.toSet()).size
                    Text(
                        text = stringResource(KMR.strings.compare_badges, mine.badges.size, friend.badges.size, shared),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Header(name: String, earned: Int, total: Int, modifier: Modifier) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(KMR.strings.ach_count, earned, total), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun Side(ladder: EggikuAchievements.Ladder, tiers: Int, leading: Boolean, modifier: Modifier) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (tiers > 0) stringResource(ladder.tierName(tiers - 1)) else "–",
                color = if (tiers > 0) ladder.metal(tiers - 1).color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (leading) FontWeight.Bold else null,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
            )
            Text(
                text = "$tiers/${ladder.thresholds.size}" + if (leading) "  👑" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
// KMK <--
