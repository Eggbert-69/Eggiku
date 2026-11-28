package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsMainScreen

    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    private fun getPalerSurface(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val dark = isSystemInDarkTheme()
        return remember(surface, dark) {
            val arr = FloatArray(3)
            ColorUtils.colorToHSL(surface.toArgb(), arr)
            arr[2] = if (dark) {
                arr[2] - 0.05f
            } else {
                arr[2] + 0.02f
            }.coerceIn(0f, 1f)
            Color.hsl(arr[0], arr[1], arr[2])
        }
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                // KMK -->
                val showAdvancedPref = remember { Injekt.get<UiPreferences>().showAdvancedSettings() }
                val showAdvanced by showAdvancedPref.collectAsState()
                // KMK <--
                // KMK -->
                // Settings grouped into a few plain sections, with the search box always on top
                val visibleSections = sections
                    .map { section ->
                        section.copy(
                            items = section.items
                                .filter { it.screen !is SearchableSettings || it.screen.isEnabled() }
                                .filter { showAdvanced || it.screen !in AdvancedOnlyScreens },
                        )
                    }
                    .filter { it.items.isNotEmpty() }
                LazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    item(key = "settings-main-search") {
                        Surface(
                            onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(KMR.strings.settings_search_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                    visibleSections.forEach { section ->
                        item(key = "settings-main-header-${section.titleRes.resourceId}") {
                            Text(
                                text = stringResource(section.titleRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 6.dp),
                            )
                        }
                        item(key = "settings-main-group-${section.titleRes.resourceId}") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                section.items.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 72.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    val selected = twoPane && navigator.items.firstOrNull()?.let {
                                        it::class == item.screen::class
                                    } == true
                                    CompositionLocalProvider(
                                        LocalContentColor provides if (selected) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            LocalContentColor.current
                                        },
                                    ) {
                                        TextPreferenceWidget(
                                            modifier = if (selected) {
                                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                            } else {
                                                Modifier
                                            },
                                            title = stringResource(item.titleRes),
                                            subtitle = item.formatSubtitle(),
                                            icon = item.icon,
                                            onPreferenceClick = { navigator.navigate(item.screen, twoPane) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "settings-main-show-advanced") {
                        SwitchPreferenceWidget(
                            title = stringResource(KMR.strings.pref_show_advanced_settings),
                            subtitle = stringResource(KMR.strings.pref_show_advanced_settings_summary),
                            checked = showAdvanced,
                            onCheckedChanged = { showAdvancedPref.set(it) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                // KMK <--
            },
        )
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource,
        val formatSubtitle: @Composable () -> String = { stringResource(subtitleRes) },
        val icon: ImageVector,
        val screen: VoyagerScreen,
    )

    // KMK -->
    private data class Section(
        val titleRes: StringResource,
        val items: List<Item>,
    )

    private val sections = listOf(
        Section(
            titleRes = KMR.strings.settings_group_look,
            items = listOf(
                Item(
                    titleRes = MR.strings.pref_category_appearance,
                    subtitleRes = MR.strings.pref_appearance_summary,
                    formatSubtitle = { EggikuSettingsSummaries.appearance() },
                    icon = Icons.Outlined.Palette,
                    screen = SettingsAppearanceScreen,
                ),
            ),
        ),
        Section(
            titleRes = KMR.strings.settings_group_reading,
            items = listOf(
                Item(
                    titleRes = MR.strings.pref_category_reader,
                    subtitleRes = MR.strings.pref_reader_summary,
                    formatSubtitle = { EggikuSettingsSummaries.reader() },
                    icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                    screen = SettingsReaderScreen,
                ),
                Item(
                    titleRes = KMR.strings.pref_category_music,
                    subtitleRes = KMR.strings.pref_music_summary,
                    icon = Icons.Outlined.MusicNote,
                    screen = SettingsMusicScreen,
                ),
            ),
        ),
        Section(
            titleRes = KMR.strings.settings_group_library,
            items = listOf(
                Item(
                    titleRes = MR.strings.pref_category_library,
                    subtitleRes = MR.strings.pref_library_summary,
                    icon = Icons.Outlined.CollectionsBookmark,
                    screen = SettingsLibraryScreen,
                ),
                Item(
                    titleRes = MR.strings.pref_category_downloads,
                    subtitleRes = MR.strings.pref_downloads_summary,
                    formatSubtitle = { EggikuSettingsSummaries.downloads() },
                    icon = Icons.Outlined.GetApp,
                    screen = SettingsDownloadScreen,
                ),
                Item(
                    titleRes = KMR.strings.settings_backup_sync,
                    subtitleRes = MR.strings.pref_backup_summary,
                    formatSubtitle = { EggikuSettingsSummaries.backup() },
                    icon = Icons.Outlined.CloudUpload,
                    screen = SettingsDataScreen,
                ),
            ),
        ),
        Section(
            titleRes = KMR.strings.settings_group_more,
            items = listOf(
                Item(
                    titleRes = KMR.strings.settings_sources_accounts,
                    subtitleRes = KMR.strings.settings_sources_accounts_summary,
                    icon = Icons.Outlined.Explore,
                    screen = EggikuSourcesAccountsScreen,
                ),
                Item(
                    titleRes = KMR.strings.settings_privacy,
                    subtitleRes = MR.strings.pref_security_summary,
                    formatSubtitle = { EggikuSettingsSummaries.privacy() },
                    icon = Icons.Outlined.Security,
                    screen = SettingsSecurityScreen,
                ),
                Item(
                    titleRes = MR.strings.pref_category_advanced,
                    subtitleRes = MR.strings.pref_advanced_summary,
                    icon = Icons.Outlined.Code,
                    screen = SettingsAdvancedScreen,
                ),
                Item(
                    titleRes = MR.strings.pref_category_about,
                    subtitleRes = StringResource(0),
                    formatSubtitle = {
                        "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
                    },
                    icon = Icons.Outlined.Info,
                    screen = AboutScreen(),
                ),
            ),
        ),
    )
    // KMK <--
}

// KMK -->
/** Settings pages hidden until "Show advanced settings" is on */
private val AdvancedOnlyScreens = setOf<VoyagerScreen>(
    SettingsConnectionScreen,
    SettingsAdvancedScreen,
)
// KMK <--
