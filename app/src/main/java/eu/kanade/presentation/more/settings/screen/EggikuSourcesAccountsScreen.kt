package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import exh.assets.EhAssets
import exh.assets.ehassets.MangadexLogo
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Eggiku: Browse, trackers, connections and MangaDex, together on one page */
object EggikuSourcesAccountsScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = EggikuSourcesAccountsScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val showAdvanced by remember { Injekt.get<UiPreferences>().showAdvancedSettings() }.collectAsState()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.settings_sources_accounts),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.browse),
                        subtitle = stringResource(MR.strings.pref_browse_summary),
                        icon = Icons.Outlined.Explore,
                        onPreferenceClick = { navigator.push(SettingsBrowseScreen) },
                    )
                }
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.pref_category_tracking),
                        subtitle = stringResource(MR.strings.pref_tracking_summary),
                        icon = Icons.Outlined.Sync,
                        onPreferenceClick = { navigator.push(SettingsTrackingScreen) },
                    )
                }
                if (showAdvanced) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(KMR.strings.pref_category_connections),
                            subtitle = stringResource(KMR.strings.pref_connections_summary),
                            icon = Icons.Outlined.Link,
                            onPreferenceClick = { navigator.push(SettingsConnectionScreen) },
                        )
                    }
                }
                if (SettingsMangadexScreen.isEnabled()) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(SYMR.strings.pref_category_mangadex),
                            subtitle = stringResource(SYMR.strings.pref_mangadex_summary),
                            icon = EhAssets.MangadexLogo,
                            onPreferenceClick = { navigator.push(SettingsMangadexScreen) },
                        )
                    }
                }
            }
        }
    }
}
// KMK <--
