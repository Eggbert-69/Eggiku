package eu.kanade.tachiyomi.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.IconButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

// KMK -->

/**
 * Eggiku quick sort: one series at a time, with your categories as big buttons underneath.
 * Tap a category and the card flies off into it, then the next one comes up, like swiping
 * through cards but with a button for every category. Tap several categories to put a series
 * in more than one, then Next.
 */
class EggikuQuickSortScreen(
    private val uncategorizedOnly: Boolean = true,
) : Screen() {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val reduceMotion = rememberReduceMotion()

        var onlyUncategorized by remember { mutableStateOf(uncategorizedOnly) }
        val categories by produceState(emptyList<Category>()) {
            value = withIOContext { Injekt.get<GetCategories>().await().filterNot { it.isSystemCategory } }
        }
        val queue by produceState<List<LibraryManga>?>(null, onlyUncategorized) {
            value = withIOContext {
                Injekt.get<GetLibraryManga>().await()
                    .distinctBy { it.id }
                    .filter { !onlyUncategorized || it.categories.all { id -> id == 0L } }
                    .sortedBy { it.manga.title.lowercase() }
            }
        }
        var index by remember(queue) { mutableStateOf(0) }
        // What was sorted, so it can be undone: (manga, categories before)
        val history = remember(queue) { mutableStateListOf<Pair<LibraryManga, List<Long>>>() }
        val picked = remember(index, queue) { mutableStateListOf<Long>() }
        val fly = remember { Animatable(0f) }
        var flyDirection by remember { mutableStateOf(1f) }

        val current = queue?.getOrNull(index)

        fun advance(assign: List<Long>?) {
            val manga = current ?: return
            scope.launch {
                if (assign != null) {
                    history.add(manga to manga.categories.filter { it != 0L })
                    withIOContext { Injekt.get<SetMangaCategories>().await(manga.id, assign) }
                    EggikuAchievements.bump(EggikuAchievements.COUNT_QUICK_SORTED)
                } else {
                    history.add(manga to manga.categories.filter { it != 0L })
                }
                if (!reduceMotion) fly.animateTo(1f, tween(220, easing = FastOutLinearInEasing))
                index++
                fly.snapTo(0f)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.quick_sort_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            enabled = history.isNotEmpty(),
                            onClick = {
                                val (manga, before) = history.removeAt(history.lastIndex)
                                scope.launch {
                                    withIOContext { Injekt.get<SetMangaCategories>().await(manga.id, before) }
                                    index = (index - 1).coerceAtLeast(0)
                                }
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(KMR.strings.quick_sort_undo))
                        }
                    },
                )
            },
        ) { padding ->
            val list = queue
            if (list == null) {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyUncategorized,
                        onClick = { onlyUncategorized = true },
                        label = { Text(stringResource(KMR.strings.quick_sort_uncategorized)) },
                    )
                    FilterChip(
                        selected = !onlyUncategorized,
                        onClick = { onlyUncategorized = false },
                        label = { Text(stringResource(KMR.strings.quick_sort_everything)) },
                    )
                }

                if (categories.isEmpty()) {
                    Text(
                        stringResource(KMR.strings.quick_sort_no_categories),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                    return@Column
                }
                if (current == null) {
                    Text(
                        text = if (list.isEmpty()) {
                            stringResource(KMR.strings.quick_sort_nothing)
                        } else {
                            stringResource(KMR.strings.quick_sort_done, history.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                    FilledTonalButton(onClick = navigator::pop) { Text(stringResource(KMR.strings.quick_sort_finish)) }
                    return@Column
                }

                Text(
                    text = stringResource(KMR.strings.quick_sort_progress, index + 1, list.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // The card: flies up and off toward the categories when sorted
                Box(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .fillMaxWidth(0.6f)
                        .graphicsLayer {
                            translationY = -fly.value * 260f
                            translationX = fly.value * 180f * flyDirection
                            rotationZ = fly.value * 18f * flyDirection
                            alpha = 1f - fly.value
                        },
                ) {
                    val manga = current.manga
                    MangaCover.Book(
                        data = MangaCoverModel(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            ogUrl = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        contentDescription = manga.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(MangaCover.Book.ratio),
                    )
                }
                Text(
                    text = current.manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(KMR.strings.quick_sort_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categories.forEachIndexed { i, category ->
                        val selected = category.id in picked
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (picked.isEmpty()) {
                                    // One tap sorts it straight away
                                    flyDirection = if (i % 2 == 0) -1f else 1f
                                    advance(listOf(category.id))
                                } else if (selected) {
                                    picked.remove(category.id)
                                } else {
                                    picked.add(category.id)
                                }
                            },
                            label = { Text(category.name, style = MaterialTheme.typography.titleSmall) },
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        flyDirection = 1f
                        advance(null)
                    }) {
                        Text(stringResource(KMR.strings.quick_sort_skip))
                    }
                    if (picked.isEmpty()) {
                        OutlinedButton(onClick = { picked.add(-1L) }) {
                            Text(stringResource(KMR.strings.quick_sort_several))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                flyDirection = 1f
                                advance(picked.filter { it > 0 })
                            },
                            enabled = picked.any { it > 0 },
                        ) {
                            Text(stringResource(KMR.strings.quick_sort_next))
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
