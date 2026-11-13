package eu.kanade.tachiyomi.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * The egg intro. The system launch screen only shows the still egg (many phones never run animated
 * launch icons), then this overlay sits exactly on top of it and plays the crack once the launch
 * screen is gone, so it looks like one continuous animation.
 */
object EggikuIntro {
    /** Flipped by MainActivity the moment the system launch screen lets go */
    val splashGone = mutableStateOf(false)

    /** True once the intro has finished (or was skipped or turned off) */
    val introDone = mutableStateOf(false)
}

private val SplashBackground = Color(0xFF111111)
private const val HOLD_AFTER_MS = 250L
private const val FADE_MS = 300

@Composable
fun EggikuIntroOverlay() {
    val enabled = remember { Injekt.get<UiPreferences>().eggikuIntroAnimation().get() }
    val reduceMotion = rememberReduceMotion()
    var show by rememberSaveable { mutableStateOf(enabled && !reduceMotion) }
    LaunchedEffect(show) { if (!show) EggikuIntro.introDone.value = true }
    if (!show) return

    val image = AnimatedImageVector.animatedVectorResource(R.drawable.eggiku_splash_egg)
    var atEnd by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }
    val splashGone by EggikuIntro.splashGone

    LaunchedEffect(splashGone) {
        if (!splashGone) return@LaunchedEffect
        atEnd = true
        delay(image.totalDuration + HOLD_AFTER_MS)
        alpha.animateTo(0f, tween(FADE_MS))
        show = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(SplashBackground)
            // Tap anywhere to skip
            .pointerInput(Unit) { detectTapGestures { show = false } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberAnimatedVectorPainter(image, atEnd),
            contentDescription = null,
            modifier = Modifier.size(288.dp),
        )
    }
}
// KMK <--
