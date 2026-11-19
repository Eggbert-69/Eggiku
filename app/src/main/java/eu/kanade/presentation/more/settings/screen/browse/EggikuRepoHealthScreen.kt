package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.search.EggikuSourceHealth
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.model.ExtensionStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// KMK -->
/**
 * Eggiku repo health: checks every extension repo and points out the ones that are down, slow, or
 * haven't changed in months, plus sources from them that keep failing searches.
 */
object EggikuRepoHealth {

    enum class Status { Ok, Slow, Stale, Down, Disabled }

    @Immutable
    data class Report(
        val store: ExtensionStore,
        val status: Status,
        val millis: Long?,
        val error: String?,
        /** When the repo last changed, if it could be found out */
        val updatedAt: Long?,
        val extensionsListed: Int,
        val installedFromIt: Int,
        val obsoleteInstalled: Int,
        val failingSources: List<String>,
    )

    private const val SLOW_MS = 5_000L
    private val STALE_MS = TimeUnit.DAYS.toMillis(90)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): List<Report> = withIOContext {
        val stores = Injekt.get<GetExtensionStores>().get()
        val disabled = Injekt.get<SourcePreferences>().disabledRepos().get()
        val manager = Injekt.get<ExtensionManager>()
        val available = withTimeoutOrNull(5_000) { manager.availableExtensionsFlow.first() }.orEmpty()
        val installed = manager.installedExtensionsFlow.value
        coroutineScope {
            stores.map { store ->
                async {
                    val fromIt = installed.filter { it.store?.indexUrl == store.indexUrl }
                    val failing = fromIt.flatMap { ext -> ext.sources }
                        .filter { EggikuSourceHealth.get(it.id).failuresInARow >= EggikuSourceHealth.FAILS_TO_SKIP }
                        .map { it.name }
                    val listed = available.count { it.store.indexUrl == store.indexUrl }
                    if (store.indexUrl in disabled) {
                        return@async Report(store, Status.Disabled, null, null, null, listed, fromIt.size, fromIt.count { it.isObsolete }, failing)
                    }
                    val (millis, error, lastModified) = ping(store.extensionListUrl ?: store.indexUrl)
                    val updatedAt = githubUpdatedAt(store.extensionListUrl ?: store.indexUrl) ?: lastModified
                    val status = when {
                        error != null -> Status.Down
                        millis != null && millis > SLOW_MS -> Status.Slow
                        updatedAt != null && System.currentTimeMillis() - updatedAt > STALE_MS -> Status.Stale
                        else -> Status.Ok
                    }
                    Report(store, status, millis, error, updatedAt, listed, fromIt.size, fromIt.count { it.isObsolete }, failing)
                }
            }.awaitAll()
        }.sortedBy { it.status == Status.Ok }
    }

    private data class Ping(val millis: Long?, val error: String?, val lastModified: Long?)

    private fun ping(url: String): Ping {
        val started = System.currentTimeMillis()
        return try {
            Injekt.get<NetworkHelper>().client.newCall(GET(url)).execute().use { response ->
                val took = System.currentTimeMillis() - started
                if (!response.isSuccessful) return Ping(took, "HTTP ${response.code}", null)
                val modified = response.header("Last-Modified")?.let {
                    runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                }
                Ping(took, null, modified)
            }
        } catch (e: Exception) {
            Ping(null, e.message ?: e::class.simpleName, null)
        }
    }

    /** For repos on GitHub (raw or jsDelivr links), the date of the branch's newest commit */
    private fun githubUpdatedAt(url: String): Long? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val segments = parsed.pathSegments
        val (owner, repo, branch) = when (parsed.host) {
            "raw.githubusercontent.com" -> if (segments.size >= 3) Triple(segments[0], segments[1], segments[2]) else return null
            "cdn.jsdelivr.net" -> if (segments.size >= 3 && segments[0] == "gh") {
                val repoAndBranch = segments[2].split('@')
                Triple(segments[1], repoAndBranch[0], repoAndBranch.getOrNull(1) ?: return null)
            } else {
                return null
            }
            else -> return null
        }
        return try {
            Injekt.get<NetworkHelper>().client
                .newCall(GET("https://api.github.com/repos/$owner/$repo/commits?sha=$branch&per_page=1"))
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return null
                    val date = json.parseToJsonElement(response.body.string()).jsonArray.firstOrNull()
                        ?.jsonObject?.get("commit")?.jsonObject?.get("committer")?.jsonObject?.get("date")
                        ?.jsonPrimitive?.contentOrNull ?: return null
                    OffsetDateTime.parse(date).toInstant().toEpochMilli()
                }
        } catch (_: Exception) {
            null
        }
    }
}

class EggikuRepoHealthScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var reports by remember { mutableStateOf<List<EggikuRepoHealth.Report>?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        var confirmRemove by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(refresh) {
            reports = null
            reports = EggikuRepoHealth.check()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.repo_health),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { refresh++ }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(KMR.strings.repo_health_check_again))
                        }
                    },
                )
            },
        ) { padding ->
            val list = reports
            if (list == null) {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            if (list.isEmpty()) {
                EmptyScreen(message = stringResource(KMR.strings.repo_health_none), modifier = Modifier.padding(padding))
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
                item(key = "summary") {
                    val problems = list.count { it.status != EggikuRepoHealth.Status.Ok && it.status != EggikuRepoHealth.Status.Disabled }
                    Text(
                        text = if (problems == 0) {
                            stringResource(KMR.strings.repo_health_all_good, list.size)
                        } else {
                            stringResource(KMR.strings.repo_health_problems, problems, list.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                items(list, key = { it.store.indexUrl }) { report ->
                    ReportCard(
                        report = report,
                        confirmingRemove = confirmRemove == report.store.indexUrl,
                        onDisable = {
                            val prefs = Injekt.get<SourcePreferences>()
                            prefs.disabledRepos().set(prefs.disabledRepos().get() + report.store.indexUrl)
                            refresh++
                        },
                        onEnable = {
                            val prefs = Injekt.get<SourcePreferences>()
                            prefs.disabledRepos().set(prefs.disabledRepos().get() - report.store.indexUrl)
                            refresh++
                        },
                        onRemove = {
                            if (confirmRemove == report.store.indexUrl) {
                                confirmRemove = null
                                scope.launch {
                                    withIOContext {
                                        val prefs = Injekt.get<SourcePreferences>()
                                        prefs.disabledRepos().set(prefs.disabledRepos().get() - report.store.indexUrl)
                                        Injekt.get<RemoveExtensionStore>()(report.store.indexUrl)
                                        Injekt.get<ExtensionManager>().findAvailableExtensions()
                                    }
                                    refresh++
                                }
                            } else {
                                confirmRemove = report.store.indexUrl
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    @Composable
    private fun ReportCard(
        report: EggikuRepoHealth.Report,
        confirmingRemove: Boolean,
        onDisable: () -> Unit,
        onEnable: () -> Unit,
        onRemove: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val (badge, color) = when (report.status) {
            EggikuRepoHealth.Status.Ok -> stringResource(KMR.strings.repo_health_ok) to Color(0xFF4CAF50)
            EggikuRepoHealth.Status.Slow -> stringResource(KMR.strings.repo_health_slow) to Color(0xFFFFB300)
            EggikuRepoHealth.Status.Stale -> stringResource(KMR.strings.repo_health_stale) to Color(0xFFFF8F00)
            EggikuRepoHealth.Status.Down -> stringResource(KMR.strings.repo_health_down) to MaterialTheme.colorScheme.error
            EggikuRepoHealth.Status.Disabled -> stringResource(KMR.strings.repo_health_disabled) to MaterialTheme.colorScheme.outline
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(report.store.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(badge, style = MaterialTheme.typography.labelLarge, color = color)
                }
                val facts = buildList {
                    report.millis?.let { add(String.format(java.util.Locale.getDefault(), "%.1f s", it / 1000f)) }
                    add(stringResource(KMR.strings.repo_health_listed, report.extensionsListed))
                    add(stringResource(KMR.strings.repo_health_installed, report.installedFromIt))
                    report.updatedAt?.let { add(stringResource(KMR.strings.repo_health_updated, daysAgo(it))) }
                }
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.error?.let { Text(stringResource(KMR.strings.repo_health_error, it), style = MaterialTheme.typography.bodySmall) }
                val advice = when (report.status) {
                    EggikuRepoHealth.Status.Down -> stringResource(KMR.strings.repo_health_advice_down)
                    EggikuRepoHealth.Status.Slow -> stringResource(KMR.strings.repo_health_advice_slow)
                    EggikuRepoHealth.Status.Stale -> stringResource(KMR.strings.repo_health_advice_stale)
                    else -> null
                }
                advice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (report.obsoleteInstalled > 0) {
                    Text(stringResource(KMR.strings.repo_health_obsolete, report.obsoleteInstalled), style = MaterialTheme.typography.bodySmall)
                }
                if (report.failingSources.isNotEmpty()) {
                    Text(
                        stringResource(KMR.strings.repo_health_failing, report.failingSources.take(5).joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (report.status != EggikuRepoHealth.Status.Ok) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (report.status == EggikuRepoHealth.Status.Disabled) {
                            OutlinedButton(onClick = onEnable) { Text(stringResource(KMR.strings.repo_health_enable)) }
                        } else {
                            OutlinedButton(onClick = onDisable) { Text(stringResource(KMR.strings.repo_health_disable)) }
                        }
                        OutlinedButton(onClick = onRemove) {
                            Text(
                                stringResource(if (confirmingRemove) KMR.strings.repo_health_remove_confirm else KMR.strings.repo_health_remove),
                                color = if (confirmingRemove) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun daysAgo(millis: Long): Int =
        TimeUnit.MILLISECONDS.toDays(Instant.now().toEpochMilli() - millis).toInt().coerceAtLeast(0)
}
// KMK <--
