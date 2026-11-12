package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
private val StarColor = Color(0xFFFFB81C)

/** Eggiku Top shelf: everything you've rated, best first, with your notes */
class EggikuTopShelfScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val entries by EggikuRatings.pref().collectAsState()
        val shelf by produceState<List<Pair<Manga, Int>>?>(null, entries) {
            value = withIOContext {
                val getManga = Injekt.get<GetManga>()
                EggikuRatings.parse(entries)
                    .mapNotNull { (id, stars) -> getManga.await(id)?.let { it to stars } }
                    .sortedWith(compareByDescending<Pair<Manga, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.eggiku_top_shelf),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val list = shelf
            when {
                list == null -> LoadingScreen(Modifier.padding(padding))
                list.isEmpty() -> EmptyScreen(
                    stringRes = KMR.strings.eggiku_top_shelf_empty,
                    modifier = Modifier.padding(padding),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    list.groupBy { it.second }.forEach { (stars, group) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header-$stars") {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                repeat(stars) {
                                    Icon(Icons.Filled.Star, null, tint = StarColor, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    text = "  ${group.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(group, key = { it.first.id }) { (manga, _) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navigator.push(MangaScreen(manga.id)) },
                            ) {
                                MangaCover.Book(data = manga, modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = manga.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                manga.notes.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
