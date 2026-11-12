package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.ThemeColorPickerWidget
import eu.kanade.presentation.theme.EggikuThemeCodec
import eu.kanade.presentation.theme.EggikuThemeColorRole
import eu.kanade.presentation.theme.themeOverride
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku theme editor: pick your own background, accent and text colors for the Custom theme,
 * and share themes as a short code.
 */
class EggikuThemeEditorScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<UiPreferences>() }
        var showImport by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.theme_editor_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(KMR.strings.theme_editor_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                ColorRow(prefs, EggikuThemeColorRole.BACKGROUND, KMR.strings.theme_editor_background) {
                    navigator.push(EggikuThemeColorPickerScreen(EggikuThemeColorRole.BACKGROUND))
                }
                ColorRow(prefs, EggikuThemeColorRole.ACCENT, KMR.strings.theme_editor_accent) {
                    navigator.push(EggikuThemeColorPickerScreen(EggikuThemeColorRole.ACCENT))
                }
                ColorRow(prefs, EggikuThemeColorRole.TEXT, KMR.strings.theme_editor_text) {
                    navigator.push(EggikuThemeColorPickerScreen(EggikuThemeColorRole.TEXT))
                }
                ListItem(
                    headlineContent = { Text(stringResource(KMR.strings.theme_editor_reset)) },
                    modifier = Modifier.clickable {
                        EggikuThemeColorRole.entries.forEach { prefs.themeOverride(it).set(0) }
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(KMR.strings.theme_export)) },
                    supportingContent = { Text(stringResource(KMR.strings.theme_export_summary)) },
                    modifier = Modifier.clickable {
                        val code = EggikuThemeCodec.export(prefs)
                        context.getSystemService<ClipboardManager>()
                            ?.setPrimaryClip(ClipData.newPlainText("Eggiku theme", code))
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, code)
                                },
                                null,
                            ),
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(KMR.strings.theme_import)) },
                    supportingContent = { Text(stringResource(KMR.strings.theme_import_summary)) },
                    modifier = Modifier.clickable { showImport = true },
                )
            }
        }

        if (showImport) {
            var code by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImport = false },
                title = { Text(stringResource(KMR.strings.theme_import)) },
                text = {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = { Text("eggiku-theme:{…}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (EggikuThemeCodec.import(prefs, code)) {
                                showImport = false
                                (context as? Activity)?.let { ActivityCompat.recreate(it) }
                            } else {
                                context.toast(KMR.strings.theme_import_invalid)
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.theme_import_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImport = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun ColorRow(
        prefs: UiPreferences,
        role: EggikuThemeColorRole,
        label: StringResource,
        onClick: () -> Unit,
    ) {
        val value by prefs.themeOverride(role).collectAsState()
        ListItem(
            headlineContent = { Text(stringResource(label)) },
            supportingContent = {
                Text(
                    if (value == 0) {
                        stringResource(KMR.strings.theme_editor_automatic)
                    } else {
                        "#%06X".format(value and 0xFFFFFF)
                    },
                )
            },
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (value == 0) Color.Transparent else Color(value), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

/** Color wheel for one part of the custom theme */
class EggikuThemeColorPickerScreen(private val role: EggikuThemeColorRole) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<UiPreferences>() }
        val controller = rememberColorPickerController()
        val pref = prefs.themeOverride(role)
        val initial = remember {
            pref.get().takeIf { it != 0 }?.let(::Color) ?: when (role) {
                EggikuThemeColorRole.BACKGROUND -> Color(0xFF15151C)
                EggikuThemeColorRole.ACCENT -> Color(prefs.colorTheme().get())
                EggikuThemeColorRole.TEXT -> Color(0xFFF2F2F2)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(
                        when (role) {
                            EggikuThemeColorRole.BACKGROUND -> KMR.strings.theme_editor_background
                            EggikuThemeColorRole.ACCENT -> KMR.strings.theme_editor_accent
                            EggikuThemeColorRole.TEXT -> KMR.strings.theme_editor_text
                        },
                    ),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                ThemeColorPickerWidget(
                    controller = controller,
                    initialColor = initial,
                    onItemClick = { color, _ ->
                        pref.set(color.toArgb())
                        prefs.appTheme().set(AppTheme.CUSTOM)
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                    },
                )
            }
        }
    }
}
// KMK <--
