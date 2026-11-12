package tachiyomi.presentation.core.components.material

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

// KMK -->

/** Eggiku: true while the hidden Glib Glorb theme is on (stars, alien icons, UFO refresh) */
val LocalGlibGlorb = staticCompositionLocalOf { false }

/** True when the phone's "Remove animations" setting is on; Eggiku's extra motion is skipped then */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Eggiku: a quick pop (grow, dip, settle) whenever [key] changes. Nothing plays the first time
 * it's shown. Runs in the draw phase only, so it never re-lays out.
 */
fun Modifier.eggikuPop(key: Any?, peak: Float = 1.18f): Modifier = composed {
    val reduceMotion = rememberReduceMotion()
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (reduceMotion) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(
            1f,
            keyframes {
                durationMillis = 240
                1f at 0
                peak at 90
                0.96f at 170
                1f at 240
            },
        )
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
// KMK <--
