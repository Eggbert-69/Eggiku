package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuAutoScroll
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

/** Small speed controls while auto-scroll is on: slower, the speed, faster, and stop */
@Composable
fun BoxScope.EggikuAutoScrollIndicator(menuVisible: Boolean) {
    val active by EggikuAutoScroll.active.collectAsState()
    val speed by EggikuAutoScroll.speed.collectAsState()
    AnimatedVisibility(
        visible = active && !menuVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.graphicsLayer { alpha = 0.7f },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = EggikuAutoScroll::slower, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(KMR.strings.auto_scroll_slower))
                }
                Text(text = EggikuAutoScroll.label(speed), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = EggikuAutoScroll::faster, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(KMR.strings.auto_scroll_faster))
                }
                IconButton(onClick = EggikuAutoScroll::off, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.auto_scroll_stop))
                }
            }
        }
    }
}
// KMK <--
