package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

// KMK -->

private const val SECRET_WORD = "wyatt"

fun isGlibGlorbSecret(query: String) = query.trim().equals(SECRET_WORD, ignoreCase = true)

private val SmokeColors = listOf(Color(0xFFE6E6E6), Color(0xFFCFCFCF), Color(0xFFBDBDBD), Color(0xFFF2F2F2))

/** One puff of smoke: where it starts, how big it gets, and when it appears (0..1) */
private data class Puff(val x: Float, val y: Float, val radius: Float, val at: Float, val color: Color)

/**
 * Eggiku: the secret rocket shown when you search "wyatt" in settings. Tap it and it blasts off,
 * leaving a trail of smoke that fills the screen; the app then switches to Glib Glorb and the
 * smoke clears (see [EggikuSmokeReveal]).
 */
@Composable
fun EggikuSecretRocket(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var launching by remember { mutableStateOf(false) }
    val flight = remember { Animatable(0f) }
    val cover = remember { Animatable(0f) }
    val wobble = remember { Animatable(0f) }

    // Idle: the rocket rocks gently, waiting to be tapped
    LaunchedEffect(launching) {
        if (launching || reduceMotion) return@LaunchedEffect
        while (true) {
            wobble.animateTo(4f, tween(500, easing = FastOutSlowInEasing))
            wobble.animateTo(-4f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(launching) {
        if (!launching) return@LaunchedEffect
        if (!reduceMotion) {
            wobble.snapTo(0f)
            // Shake on the pad, then lift off
            repeat(6) {
                wobble.animateTo(if (it % 2 == 0) 6f else -6f, tween(45))
            }
            wobble.snapTo(0f)
            launch { flight.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
            delay(650)
            cover.animateTo(1f, tween(650, easing = LinearEasing))
        }
        val uiPreferences = Injekt.get<UiPreferences>()
        uiPreferences.glibGlorbUnlocked().set(true)
        uiPreferences.glibGlorbRevealPending().set(!reduceMotion)
        uiPreferences.appTheme().set(AppTheme.GLIB_GLORB)
        (context as? Activity)?.let { ActivityCompat.recreate(it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val padY = heightPx * 0.42f
        val rocketTop = padY - flight.value * (padY + 200f)

        // Smoke: a trail behind the rocket, then big billows that fill the screen
        val puffs = remember(widthPx, heightPx) {
            val random = Random(7)
            val trail = (0 until 26).map { i ->
                val t = i / 26f
                Puff(
                    x = widthPx / 2 + (random.nextFloat() - 0.5f) * 90f,
                    y = padY + 60f - t * (padY + 60f),
                    radius = 40f + random.nextFloat() * 50f,
                    at = t * 0.9f,
                    color = SmokeColors[random.nextInt(SmokeColors.size)],
                )
            }
            val billows = (0 until 34).map {
                Puff(
                    x = random.nextFloat() * widthPx,
                    y = random.nextFloat() * heightPx,
                    radius = maxOf(widthPx, heightPx) * (0.25f + random.nextFloat() * 0.3f),
                    at = random.nextFloat(),
                    color = SmokeColors[random.nextInt(SmokeColors.size)],
                )
            }
            trail to billows
        }

        if (launching) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val f = flight.value
                puffs.first.forEach { puff ->
                    if (f < puff.at) return@forEach
                    val grow = ((f - puff.at) / 0.4f).coerceIn(0f, 1f)
                    drawCircle(puff.color, radius = puff.radius * (0.3f + grow), center = Offset(puff.x, puff.y), alpha = 0.9f)
                }
                val c = cover.value
                puffs.second.forEach { puff ->
                    if (c < puff.at * 0.6f) return@forEach
                    val grow = ((c - puff.at * 0.6f) / 0.4f).coerceIn(0f, 1f)
                    drawCircle(puff.color, radius = puff.radius * grow, center = Offset(puff.x, puff.y))
                }
                if (c >= 0.95f) drawRect(Color(0xFFE6E6E6), alpha = (c - 0.95f) * 20f)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = rocketTop
                    rotationZ = wobble.value - 45f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clickable(enabled = !launching) { launching = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.RocketLaunch,
                    contentDescription = stringResource(KMR.strings.glib_glorb_rocket),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        if (!launching) {
            Text(
                text = stringResource(KMR.strings.glib_glorb_rocket_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 140.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

/**
 * Eggiku: after the rocket launch, the app comes back in Glib Glorb under a blanket of smoke
 * that drifts apart to reveal it.
 */
@Composable
fun EggikuSmokeReveal() {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    var show by remember { mutableStateOf(uiPreferences.glibGlorbRevealPending().get()) }
    if (!show) return
    val clear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        uiPreferences.glibGlorbRevealPending().set(false)
        delay(250)
        clear.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        show = false
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val puffs = remember(w, h) {
            val random = Random(11)
            (0 until 40).map {
                Puff(
                    x = random.nextFloat() * w,
                    y = random.nextFloat() * h,
                    radius = maxOf(w, h) * (0.25f + random.nextFloat() * 0.3f),
                    at = random.nextFloat(),
                    color = SmokeColors[random.nextInt(SmokeColors.size)],
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = clear.value
            drawRect(Color(0xFFE6E6E6), alpha = (1f - c * 4f).coerceIn(0f, 1f))
            puffs.forEach { puff ->
                // Each puff drifts away from the middle while it thins out
                val dx = (puff.x - w / 2) * c * 0.9f
                val dy = (puff.y - h / 2) * c * 0.9f - c * 120f
                val alpha = (1f - c * (1.2f + puff.at)).coerceIn(0f, 1f)
                if (alpha <= 0f) return@forEach
                drawCircle(
                    puff.color,
                    radius = puff.radius * (1f + c * 0.4f),
                    center = Offset(puff.x + dx, puff.y + dy),
                    alpha = alpha,
                )
            }
        }
    }
}
// KMK <--
