package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.shelves.EggikuForYou
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import exh.recs.RecommendsScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

// KMK -->

/** Eggiku "More like this": because you rated X, from your pile, and what friends rated best */
class EggikuForYouScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val result by produceState<EggikuForYou.Result?>(null) {
            EggikuForYou.refreshMyTopTitles()
            value = EggikuForYou.build()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.for_you_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val data = result
            when {
                data == null -> LoadingScreen(Modifier.padding(padding))
                data.because.isEmpty() && data.pile.isEmpty() && data.friends.isEmpty() -> EmptyScreen(
                    stringRes = KMR.strings.for_you_empty,
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (data.because.isEmpty()) {
                        item(key = "rate-hint") {
                            Hint(stringResource(KMR.strings.for_you_rate_hint))
                        }
                    }
                    items(data.because, key = { "b${it.liked.id}" }) { row ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        KMR.strings.for_you_because,
                                        row.liked.manga.title,
                                        "★".repeat(row.stars),
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        navigator.push(
                                            RecommendsScreen(
                                                RecommendsScreen.Args.SingleSourceManga(
                                                    row.liked.manga.id,
                                                    row.liked.manga.source,
                                                ),
                                            ),
                                        )
                                    },
                                ) {
                                    Text(stringResource(KMR.strings.for_you_find_more))
                                }
                            }
                            if (row.picks.isEmpty()) {
                                Hint(stringResource(KMR.strings.for_you_nothing_in_pile))
                            } else {
                                CoverRow(row.picks, navigator)
                            }
                        }
                    }
                    if (data.pile.isNotEmpty()) {
                        item(key = "pile") {
                            Column {
                                Header(stringResource(KMR.strings.for_you_pile))
                                CoverRow(data.pile, navigator)
                            }
                        }
                    }
                    if (data.friends.isNotEmpty()) {
                        item(key = "friends-header") {
                            Header(stringResource(KMR.strings.for_you_friends))
                        }
                        items(data.friends, key = { "f${it.friend}|${it.title}" }) { pick ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val id = pick.mangaId
                                        if (id != null) {
                                            navigator.push(MangaScreen(id))
                                        } else {
                                            navigator.push(GlobalSearchScreen(pick.title))
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(pick.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(
                                        if (pick.mangaId != null) {
                                            KMR.strings.for_you_friend_in_library
                                        } else {
                                            KMR.strings.for_you_friend_search
                                        },
                                        pick.friend,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item(key = "friends-hint") {
                            Hint(stringResource(KMR.strings.for_you_friends_hint))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    @Composable
    private fun CoverRow(picks: List<EggikuForYou.Pick>, navigator: Navigator) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(picks, key = { it.manga.id }) { pick ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clickable { navigator.push(MangaScreen(pick.manga.id)) },
                ) {
                    MangaCover.Book(data = pick.manga.manga, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = pick.manga.manga.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (pick.shared.isNotEmpty()) {
                        Text(
                            text = pick.shared.take(3).joinToString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
// KMK <--
