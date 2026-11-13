package eu.kanade.tachiyomi.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.eggikuupdate.EggikuUpdates
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

// KMK -->

/** Eggiku: what's installed against the newest Eggiku build and Komikku's latest stable */
class EggikuUpdateScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var refresh by remember { mutableIntStateOf(0) }
        var status by remember { mutableStateOf<EggikuUpdates.Status?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val allBranches by EggikuUpdates.followAllBranches().collectAsState()
        val autoCheck by EggikuUpdates.autoCheck().collectAsState()

        LaunchedEffect(refresh, allBranches) {
            status = null
            error = null
            try {
                status = EggikuUpdates.check()
                status?.latest?.let { EggikuUpdates.lastSeenTag().set("build-${it.branch}-${it.number}") }
            } catch (e: Exception) {
                error = e.message ?: e::class.simpleName
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.eggiku_updates),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val current = status
            if (current == null && error == null) {
                LoadingScreen(Modifier.padding(padding))
                return@Scaffold
            }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) {
                    Text(stringResource(KMR.strings.eggiku_update_error, error.orEmpty()))
                    OutlinedButton(onClick = { refresh++ }) { Text(stringResource(KMR.strings.eggiku_update_retry)) }
                    return@Column
                }
                current!!
                InfoCard(
                    title = stringResource(KMR.strings.eggiku_update_installed),
                    lines = listOf(
                        current.installed?.title ?: stringResource(KMR.strings.eggiku_update_unknown_build),
                        stringResource(KMR.strings.eggiku_update_based_on, EggikuUpdates.baseVersion),
                    ),
                )
                InfoCard(
                    title = stringResource(KMR.strings.eggiku_update_latest),
                    lines = listOfNotNull(
                        current.latest?.title ?: stringResource(KMR.strings.eggiku_update_none),
                        current.latest?.published?.take(10),
                        if (current.updateAvailable) {
                            stringResource(KMR.strings.eggiku_update_available)
                        } else {
                            stringResource(KMR.strings.eggiku_update_up_to_date)
                        },
                    ),
                )
                val komikku = current.komikkuLatest
                InfoCard(
                    title = stringResource(KMR.strings.eggiku_update_komikku),
                    lines = listOf(
                        when {
                            komikku == null -> stringResource(KMR.strings.eggiku_update_komikku_unknown)
                            komikku == EggikuUpdates.baseVersion ->
                                stringResource(KMR.strings.eggiku_update_komikku_same, komikku)
                            else -> stringResource(KMR.strings.eggiku_update_komikku_newer, komikku, EggikuUpdates.baseVersion)
                        },
                    ),
                )
                val latest = current.latest
                if (latest != null && current.updateAvailable) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            AppUpdateDownloadJob.start(context, latest.apkUrl, latest.title)
                            EggikuAchievements.bump(EggikuAchievements.COUNT_UPDATES)
                            context.toast(KMR.strings.eggiku_update_downloading)
                        },
                    ) {
                        Text(stringResource(KMR.strings.eggiku_update_install, latest.title))
                    }
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { refresh++ }) {
                    Text(stringResource(KMR.strings.eggiku_update_check_again))
                }
                SwitchPreferenceWidget(
                    title = stringResource(KMR.strings.eggiku_update_all_branches),
                    subtitle = stringResource(KMR.strings.eggiku_update_all_branches_summary),
                    checked = allBranches,
                    onCheckedChanged = { EggikuUpdates.followAllBranches().set(it) },
                )
                SwitchPreferenceWidget(
                    title = stringResource(KMR.strings.eggiku_update_auto),
                    subtitle = stringResource(KMR.strings.eggiku_update_auto_summary),
                    checked = autoCheck,
                    onCheckedChanged = { EggikuUpdates.autoCheck().set(it) },
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            lines.forEachIndexed { i, line ->
                Text(
                    line,
                    style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (i == 0) FontWeight.Medium else null,
                )
            }
        }
    }
}
// KMK <--
