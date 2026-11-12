package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.music.EggikuPlaylistDialog
import eu.kanade.tachiyomi.data.music.EggikuMusic
import eu.kanade.tachiyomi.data.music.EggikuMusicPreferences
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Eggiku: Settings → Music */
object SettingsMusicScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsMusicScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_music

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { Injekt.get<EggikuMusicPreferences>() }

        // Notification access can change in system settings, so check again whenever we come back
        var hasAccess by remember { mutableStateOf(EggikuMusic.isAccessGranted(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasAccess = EggikuMusic.isAccessGranted(context)
                    EggikuMusic.recheckAccess(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        return listOf(
            getAccessGroup(hasAccess),
            getPlayerGroup(preferences),
            getCategoryPlaylistsGroup(preferences),
        )
    }

    @Composable
    private fun getAccessGroup(hasAccess: Boolean): Preference.PreferenceGroup {
        val context = LocalContext.current
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.music_access_group),
            preferenceItems = listOf(
                if (hasAccess) {
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.music_access_on),
                        subtitle = stringResource(KMR.strings.music_access_on_summary),
                        onClick = { context.startActivity(EggikuMusic.accessSettingsIntent()) },
                    )
                } else {
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.music_access_off),
                        subtitle = stringResource(KMR.strings.music_access_off_summary),
                        onClick = { context.startActivity(EggikuMusic.accessSettingsIntent()) },
                    )
                },
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.music_access_note)),
            ).toImmutableList(),
        )
    }

    @Composable
    private fun getPlayerGroup(preferences: EggikuMusicPreferences): Preference.PreferenceGroup {
        val miniPlayer by preferences.miniPlayer().collectPrefAsState()
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.music_player_group),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.miniPlayer(),
                    title = stringResource(KMR.strings.pref_music_mini_player),
                    subtitle = stringResource(KMR.strings.pref_music_mini_player_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.pauseWhenLeavingReader(),
                    title = stringResource(KMR.strings.pref_music_pause_on_leave),
                    subtitle = stringResource(KMR.strings.pref_music_pause_on_leave_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fadeOutAfter30Minutes(),
                    title = stringResource(KMR.strings.pref_music_fade_out),
                    subtitle = stringResource(KMR.strings.pref_music_fade_out_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.bounceWhenSettling(),
                    title = stringResource(KMR.strings.pref_music_bounce),
                    subtitle = stringResource(KMR.strings.pref_music_bounce_summary),
                    enabled = miniPlayer,
                ),
            ).toImmutableList(),
        )
    }

    @Composable
    private fun getCategoryPlaylistsGroup(preferences: EggikuMusicPreferences): Preference.PreferenceGroup {
        val categories by remember { Injekt.get<GetCategories>().subscribe() }.collectAsState(emptyList())
        val entries by preferences.categoryPlaylists().collectPrefAsState()
        val playlists = remember(entries) { EggikuMusicPreferences.parseCategoryPlaylists(entries) }
        var editing by remember { mutableStateOf<String?>(null) }

        editing?.let { name ->
            EggikuPlaylistDialog(
                title = stringResource(KMR.strings.music_category_playlist_title, name),
                initialUrl = playlists[name].orEmpty(),
                onDismiss = { editing = null },
                onSave = { preferences.setCategoryPlaylist(name, it) },
            )
        }

        val items = categories
            .filterNot { it.isSystemCategory }
            .map { category ->
                Preference.PreferenceItem.TextPreference(
                    title = category.name,
                    subtitle = playlists[category.name] ?: stringResource(KMR.strings.music_no_playlist),
                    onClick = { editing = category.name },
                )
            }
            .ifEmpty {
                listOf(Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.music_no_categories)))
            }

        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.music_category_playlists_group),
            preferenceItems = (
                listOf(Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.music_category_playlists_note))) +
                    items
                ).toImmutableList(),
        )
    }
}
// KMK <--
