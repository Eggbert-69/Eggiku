package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuSharpen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.util.system.EggikuFoldState
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
/** Eggiku: sharpening and full-resolution pages, saved separately for folded and unfolded */
@Composable
internal fun EggikuSharpnessSection(screenModel: ReaderSettingsScreenModel) {
    val prefs = screenModel.preferences
    val screen = stringResource(
        if (EggikuFoldState.isUnfolded()) KMR.strings.eggiku_screen_unfolded else KMR.strings.eggiku_screen_folded,
    )
    HeadingItem(stringResource(KMR.strings.eggiku_page_sharpness, screen))

    CheckboxItem(
        label = stringResource(KMR.strings.eggiku_full_res_pages),
        pref = prefs.eggikuFullResPages(),
    )

    if (!EggikuSharpen.isSupported) {
        Text(
            text = stringResource(KMR.strings.eggiku_sharpen_unsupported),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
        )
        return
    }

    val sharpen by prefs.eggikuSharpen().collectAsState()
    SliderItem(
        value = sharpen,
        valueRange = 0..100,
        steps = 0,
        label = stringResource(KMR.strings.eggiku_sharpen),
        valueString = if (sharpen == 0) stringResource(KMR.strings.eggiku_off) else "$sharpen%",
        onChange = { prefs.eggikuSharpen().set(it) },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    if (sharpen > 0) {
        DisposableEffect(Unit) { onDispose { EggikuSharpen.comparing.value = false } }
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsItemsPaddings.Horizontal, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            EggikuSharpen.comparing.value = true
                            tryAwaitRelease()
                            EggikuSharpen.comparing.value = false
                        },
                    )
                },
        ) {
            Text(
                text = stringResource(KMR.strings.eggiku_hold_to_compare),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }
}
// KMK <--
