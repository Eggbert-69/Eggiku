package eu.kanade.presentation.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.smart.EggikuSmart
import eu.kanade.tachiyomi.data.tags.EggikuTags
import eu.kanade.tachiyomi.ui.library.EggikuSmartRulesScreen
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel

// KMK -->
/**
 * Eggiku Smart tab: every smart category as a row of covers. Tap "See all" to open a row into a
 * full grid. Nothing here changes your categories.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EggikuSmartTab(
    items: List<LibraryItem>,
    contentPadding: PaddingValues,
    onClickManga: (LibraryManga) -> Unit,
    onLongClickManga: (LibraryManga) -> Unit,
) {
    val navigator = LocalNavigator.current
    val rulesRaw by EggikuSmart.rulesPref().collectAsState()
    val ratingsRaw by EggikuRatings.pref().collectAsState()
    val tagsRaw by EggikuTags.assignmentsPref().collectAsState()
    val trackedIds by produceState(emptySet<Long>(), items) {
        value = withIOContext {
            try {
                Injekt.get<GetTracks>().await().map { it.mangaId }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
    val sections = remember(items, rulesRaw, ratingsRaw, tagsRaw, trackedIds) {
        val ctx = EggikuSmart.Context()
        val unique = items.distinctBy { it.id }
        EggikuSmart.parse(rulesRaw).filterNot { it.hidden }.map { rule ->
            val matching = unique.filter {
                EggikuSmart.matches(
                    rule,
                    EggikuSmart.Item(it.libraryManga, it.downloadCount, it.id in trackedIds),
                    ctx,
                )
            }
            rule to EggikuSmart.sort(rule, matching, { it.libraryManga }, ctx)
        }.filter { it.second.isNotEmpty() }
    }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sections.isEmpty()) {
            item {
                Text(
                    text = stringResource(KMR.strings.smart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        sections.forEach { (rule, list) ->
            item(key = "smart-${rule.id}") {
                val open = expanded[rule.id] == true
                Column(modifier = Modifier.animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✨ " + ruleName(rule) + "  " + list.size,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { expanded[rule.id] = !open }) {
                            Text(stringResource(if (open) KMR.strings.smart_show_less else KMR.strings.smart_see_all))
                        }
                    }
                    if (open) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            list.forEach { SmartCover(it.libraryManga, onClickManga, onLongClickManga) }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list.take(ROW_LIMIT), key = { it.id }) {
                                SmartCover(it.libraryManga, onClickManga, onLongClickManga)
                            }
                        }
                    }
                }
            }
        }
        item(key = "smart-edit") {
            OutlinedButton(
                onClick = { navigator?.push(EggikuSmartRulesScreen()) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null)
                Text(stringResource(KMR.strings.smart_edit), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun ruleName(rule: EggikuSmart.Rule): String =
    rule.name.ifBlank { EggikuSmart.presetNames[rule.id]?.let { stringResource(it) } ?: rule.id }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartCover(
    manga: LibraryManga,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .combinedClickable(onClick = { onClick(manga) }, onLongClick = { onLongClick(manga) }),
    ) {
        MangaCover.Book(
            data = MangaCoverModel(
                mangaId = manga.manga.id,
                sourceId = manga.manga.source,
                isMangaFavorite = manga.manga.favorite,
                ogUrl = manga.manga.thumbnailUrl,
                lastModified = manga.manga.coverLastModified,
            ),
            contentDescription = manga.manga.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = manga.manga.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val ROW_LIMIT = 30
// KMK <--
