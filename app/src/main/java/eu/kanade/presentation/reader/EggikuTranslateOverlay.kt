package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuTranslate
import kotlinx.coroutines.delay
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/**
 * Translations themselves are drawn on each page (so they scroll and zoom with it); this only
 * shows a small note when a language pack is downloading or something went wrong. It hides
 * while a page is being read so it never gets read as page text.
 */
@Composable
fun EggikuTranslateOverlay() {
    val active by EggikuTranslate.active.collectAsState()
    val status by EggikuTranslate.status.collectAsState()
    val capturing by EggikuTranslate.capturing.collectAsState()

    val failed = status as? EggikuTranslate.Status.Failed
    LaunchedEffect(failed) {
        if (failed != null) {
            delay(5_000)
            EggikuTranslate.clearFailure()
        }
    }

    AnimatedVisibility(
        visible = active && !capturing && status != EggikuTranslate.Status.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(top = 72.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val downloading = status == EggikuTranslate.Status.Downloading
                    if (downloading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = when (val s = status) {
                            is EggikuTranslate.Status.Failed -> stringResource(KMR.strings.translate_failed, s.message.orEmpty())
                            else -> stringResource(KMR.strings.translate_downloading)
                        },
                        modifier = Modifier.padding(start = if (downloading) 10.dp else 0.dp),
                    )
                }
            }
        }
    }
}
// KMK <--
