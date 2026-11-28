package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.EggikuThemeMood
import eu.kanade.domain.ui.model.HiddenAppThemes
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.inMood
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreviewItem
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Remembers the theme you had before a tap, so the picker can offer to undo it after the restart */
internal object EggikuThemeUndo {
    var previous: AppTheme? = null
    var changedAt: Long = 0L
    const val WINDOW_MS = 10_000L
}

/**
 * Eggiku theme picker: every theme as a mini preview, with mood filters. Tapping one switches the
 * whole app to it straight away (with an undo for a few seconds); holding one shows a bigger preview.
 */
class EggikuThemePickerScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<UiPreferences>() }
        val themeMode by prefs.themeMode().collectAsState()
        val appTheme by prefs.appTheme().collectAsState()
        val amoled by prefs.themeDarkAmoled().collectAsState()
        var mood by rememberSaveable { mutableStateOf(EggikuThemeMood.ALL) }
        var bigPreview by remember { mutableStateOf<AppTheme?>(null) }
        var undoTheme by remember {
            mutableStateOf(
                EggikuThemeUndo.previous.takeIf {
                    System.currentTimeMillis() - EggikuThemeUndo.changedAt < EggikuThemeUndo.WINDOW_MS
                },
            )
        }
        LaunchedEffect(undoTheme) {
            if (undoTheme != null) {
                delay(EggikuThemeUndo.WINDOW_MS)
                undoTheme = null
                EggikuThemeUndo.previous = null
            }
        }

        val themes = remember(mood) {
            AppTheme.entries
                .filterNot { it.titleRes == null }
                .filterNot { it in HiddenAppThemes && !prefs.isHiddenThemeUnlocked(it) }
                .filter { it.inMood(mood) }
        }

        fun apply(theme: AppTheme, rememberUndo: Boolean = true) {
            if (theme == appTheme) return
            if (rememberUndo) {
                EggikuThemeUndo.previous = appTheme
                EggikuThemeUndo.changedAt = System.currentTimeMillis()
            } else {
                EggikuThemeUndo.previous = null
            }
            prefs.appTheme().set(theme)
            (context as? Activity)?.let { ActivityCompat.recreate(it) }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_app_theme),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppThemeModePreferenceWidget(
                        value = themeMode,
                        onItemClick = {
                            prefs.themeMode().set(it)
                            setAppCompatDelegateThemeMode(it)
                        },
                    )
                }
                undoTheme?.let { previous ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(KMR.strings.theme_picker_switched, themeName(appTheme)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { apply(previous, rememberUndo = false) }) {
                                    Text(stringResource(KMR.strings.theme_picker_undo))
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EggikuThemeMood.entries) { option ->
                            FilterChip(
                                selected = mood == option,
                                onClick = { mood = option },
                                label = { Text(stringResource(moodLabel(option))) },
                            )
                        }
                    }
                }
                items(themes, key = { it.name }) { theme ->
                    Column {
                        TachiyomiTheme(appTheme = theme, amoled = amoled) {
                            AppThemePreviewItem(
                                selected = theme == appTheme,
                                onClick = { apply(theme) },
                                onLongClick = { bigPreview = theme },
                            )
                        }
                        Text(
                            text = themeName(theme),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListItem(
                        headlineContent = { Text(stringResource(MR.strings.pref_dark_theme_pure_black)) },
                        supportingContent = { Text(stringResource(KMR.strings.theme_picker_pure_black_summary)) },
                        trailingContent = {
                            Switch(
                                checked = amoled,
                                enabled = themeMode != ThemeMode.LIGHT,
                                onCheckedChange = {
                                    prefs.themeDarkAmoled().set(it)
                                    (context as? Activity)?.let { activity -> ActivityCompat.recreate(activity) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }

        bigPreview?.let { theme ->
            AlertDialog(
                onDismissRequest = { bigPreview = null },
                title = { Text(themeName(theme)) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(200.dp)) {
                            TachiyomiTheme(appTheme = theme, amoled = amoled) {
                                AppThemePreviewItem(selected = theme == appTheme, onClick = {})
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            bigPreview = null
                            apply(theme)
                        },
                    ) {
                        Text(stringResource(KMR.strings.theme_picker_use))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bigPreview = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun themeName(theme: AppTheme): String = theme.titleRes?.let { stringResource(it) } ?: theme.name

private fun moodLabel(mood: EggikuThemeMood) = when (mood) {
    EggikuThemeMood.ALL -> KMR.strings.theme_mood_all
    EggikuThemeMood.NEW -> KMR.strings.theme_mood_new
    EggikuThemeMood.COZY -> KMR.strings.theme_mood_cozy
    EggikuThemeMood.BOLD -> KMR.strings.theme_mood_bold
    EggikuThemeMood.CALM -> KMR.strings.theme_mood_calm
    EggikuThemeMood.EGGIKU -> KMR.strings.theme_mood_eggiku
}
// KMK <--
