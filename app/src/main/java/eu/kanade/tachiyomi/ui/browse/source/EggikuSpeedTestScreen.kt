package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku source speed test: loads the first page of each source and times it, fastest first.
 * Nothing is saved or changed; it only reads one page from each.
 */
class EggikuSpeedTestScreen : Screen() {

    private sealed interface Result {
        data object Waiting : Result
        data object Running : Result
        data class Done(val millis: Long) : Result
        data class Failed(val reason: String) : Result
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val sourceManager = remember { Injekt.get<SourceManager>() }
        var libraryOnly by remember { mutableStateOf(true) }
        val results = remember { mutableStateMapOf<Long, Result>() }
        var job by remember { mutableStateOf<Job?>(null) }
        var sources by remember { mutableStateOf(emptyList<CatalogueSource>()) }

        fun start() {
            job?.cancel()
            job = scope.launch {
                val all: List<CatalogueSource> = sourceManager.getOnlineSources()
                sources = if (libraryOnly) {
                    val ids = withIOContext { Injekt.get<GetLibraryManga>().await().map { it.manga.source }.toSet() }
                    all.filter { it.id in ids }
                } else {
                    all
                }
                results.clear()
                sources.forEach { results[it.id] = Result.Waiting }
                val gate = Semaphore(4)
                sources.map { source ->
                    launch {
                        gate.withPermit {
                            results[source.id] = Result.Running
                            val started = System.currentTimeMillis()
                            results[source.id] = try {
                                withIOContext { withTimeout(TIMEOUT_MS) { source.getPopularManga(1) } }
                                Result.Done(System.currentTimeMillis() - started)
                            } catch (e: Exception) {
                                Result.Failed(e.message?.take(60) ?: e::class.simpleName.orEmpty())
                            }
                        }
                    }
                }
            }
        }

        val ranked = sources.sortedWith(
            compareBy<CatalogueSource> {
                when (val r = results[it.id]) {
                    is Result.Done -> r.millis
                    is Result.Failed -> Long.MAX_VALUE
                    else -> Long.MAX_VALUE - 1
                }
            },
        )
        val finished = results.values.count { it is Result.Done || it is Result.Failed }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.speed_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                item {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(KMR.strings.speed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = libraryOnly,
                                onClick = { libraryOnly = true },
                                label = { Text(stringResource(KMR.strings.speed_library_only)) },
                            )
                            FilterChip(
                                selected = !libraryOnly,
                                onClick = { libraryOnly = false },
                                label = { Text(stringResource(KMR.strings.speed_all)) },
                            )
                            TextButton(onClick = ::start) { Text(stringResource(KMR.strings.speed_run)) }
                        }
                        if (sources.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { finished.toFloat() / sources.size },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                items(ranked, key = { it.id }) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(source.lang.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (val r = results[source.id]) {
                            is Result.Done -> Text(
                                text = "${r.millis} ms",
                                color = when {
                                    r.millis < 1500 -> Color(0xFF4CAF50)
                                    r.millis < 4000 -> Color(0xFFFFB300)
                                    else -> Color(0xFFE57373)
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            is Result.Failed -> Text(
                                text = stringResource(KMR.strings.speed_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Result.Running -> Text("…", style = MaterialTheme.typography.titleSmall)
                            else -> Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
    }
}
// KMK <--
