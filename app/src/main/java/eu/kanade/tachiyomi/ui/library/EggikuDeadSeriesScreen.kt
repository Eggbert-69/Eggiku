package eu.kanade.tachiyomi.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.config.MigrationConfigScreen
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

// KMK -->
/**
 * Eggiku dead series finder: series that aren't finished but haven't had a new chapter in a year.
 * Usually the source stopped updating them, so moving to another source brings them back.
 */
object EggikuDeadSeries {

    private const val DEAD_AFTER_DAYS = 365L
    private val finished = setOf(
        SManga.COMPLETED.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
        SManga.CANCELLED.toLong(),
    )

    suspend fun find(): List<LibraryManga> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DEAD_AFTER_DAYS)
        return Injekt.get<GetLibraryManga>().await()
            .distinctBy { it.id }
            .filter { it.manga.status !in finished }
            .filter { it.latestUpload in 1..<cutoff }
            .sortedBy { it.latestUpload }
    }

    fun monthsSince(millis: Long): Int =
        (TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - millis) / 30).toInt()
}

class EggikuDeadSeriesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val found by produceState<List<LibraryManga>?>(null) {
            value = withIOContext { EggikuDeadSeries.find() }
        }
        val selected = remember(found) { mutableStateListOf<Long>().apply { found?.let { addAll(it.map { m -> m.id }) } } }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.eggiku_dead_series),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                if (!found.isNullOrEmpty()) {
                    FilledTonalButton(
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = { navigator.push(MigrationConfigScreen(selected.toList())) },
                    ) {
                        Text(stringResource(KMR.strings.eggiku_dead_series_migrate, selected.size))
                    }
                }
            },
        ) { padding ->
            val list = found
            when {
                list == null -> LoadingScreen(Modifier.padding(padding))
                list.isEmpty() -> EmptyScreen(
                    stringRes = KMR.strings.eggiku_dead_series_none,
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(contentPadding = padding) {
                    item {
                        Text(
                            text = stringResource(KMR.strings.eggiku_dead_series_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(list, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navigator.push(MangaScreen(item.id)) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MangaCover.Book(data = item.manga, modifier = Modifier.width(48.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.manga.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val months = EggikuDeadSeries.monthsSince(item.latestUpload)
                                Text(
                                    text = sourceManager.getOrStub(item.manga.source).name + " · " +
                                        pluralStringResource(KMR.plurals.eggiku_dead_months, months, months),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = item.id in selected,
                                onCheckedChange = { if (it) selected += item.id else selected -= item.id },
                            )
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
