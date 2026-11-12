package eu.kanade.tachiyomi.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// KMK -->
/** Eggiku achievements: every badge, earned ones in colour with the date, the rest with progress */
class EggikuAchievementsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val data by produceState<Pair<EggikuAchievements.Facts, Map<String, java.time.LocalDate>>?>(null) {
            value = withIOContext {
                EggikuAchievements.checkNew()
                EggikuAchievements.facts() to EggikuAchievements.earned()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.ach_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val (facts, earned) = data ?: run {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            val sorted = EggikuAchievements.all.sortedBy { if (it.key in earned) 0 else 1 }
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(KMR.strings.ach_count, earned.size, EggikuAchievements.all.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                items(sorted, key = { it.key }) { achievement ->
                    val date = earned[achievement.key]
                    val (have, need) = achievement.progress(facts)
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
                                text = achievement.emoji,
                                fontSize = 36.sp,
                                modifier = Modifier
                                    .alpha(if (date != null) 1f else 0.35f)
                                    .eggikuPop(date != null, peak = 1.3f),
                            )
                            Text(
                                text = stringResource(achievement.title),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(achievement.description),
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
                            } else {
                                LinearProgressIndicator(
                                    progress = { (have.toFloat() / need).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
