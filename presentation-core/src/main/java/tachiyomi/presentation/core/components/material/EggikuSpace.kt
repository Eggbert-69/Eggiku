package tachiyomi.presentation.core.components.material

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

// KMK -->

private val StarColors = listOf(Color.White, Color.White, Color.White, Color(0xFFBFEFFF), Color(0xFFD8FFCF))
private val AlienGreen = Color(0xFF7CFF6B)

/**
 * Eggiku Glib Glorb: stars behind the content. The stars stay still (so nothing redraws while
 * you read); every so often a shooting star crosses, and only then does it animate.
 */
@Composable
fun EggikuStarfield(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val shooting = remember { Animatable(0f) }
    var shotSeed by remember { mutableLongStateOf(1L) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(7_000, 16_000))
            shotSeed = Random.nextLong()
            shooting.snapTo(0f)
            shooting.animateTo(1f, tween(900, easing = LinearEasing))
            shooting.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // Same stars every time for a given screen size
        val random = Random(42)
        val count = ((size.width * size.height) / (90.dp.toPx() * 90.dp.toPx())).toInt().coerceIn(40, 260)
        repeat(count) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val radius = (0.5f + random.nextFloat() * 1.1f).dp.toPx() * 0.6f
            drawCircle(
                color = StarColors[random.nextInt(StarColors.size)],
                radius = radius,
                center = Offset(x, y),
                alpha = 0.3f + random.nextFloat() * 0.6f,
            )
        }
        // A small ringed planet near the top
        val planet = Offset(size.width * 0.82f, size.height * 0.12f)
        val planetRadius = 14.dp.toPx()
        drawCircle(color = Color(0xFF14210F), radius = planetRadius, center = planet)
        drawCircle(color = AlienGreen, radius = planetRadius, center = planet, alpha = 0.5f, style = Stroke(1.dp.toPx()))
        drawOval(
            color = AlienGreen,
            topLeft = Offset(planet.x - planetRadius * 1.8f, planet.y - planetRadius * 0.3f),
            size = Size(planetRadius * 3.6f, planetRadius * 0.6f),
            alpha = 0.35f,
            style = Stroke(1.dp.toPx()),
        )

        val p = shooting.value
        if (p > 0f) {
            val shot = Random(shotSeed)
            val start = Offset(size.width * (0.1f + shot.nextFloat() * 0.6f), size.height * shot.nextFloat() * 0.4f)
            val travel = Offset(size.width * 0.45f, size.height * 0.22f)
            val head = start + travel * p
            val tail = start + travel * (p - 0.18f).coerceAtLeast(0f)
            drawLine(
                color = Color.White,
                start = tail,
                end = head,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = (1f - p).coerceIn(0f, 1f) * 0.9f + 0.1f,
            )
        }
    }
}

/** Eggiku Glib Glorb: the little flying saucer used for pull-to-refresh */
@Composable
fun EggikuUfo(spinning: Boolean, modifier: Modifier = Modifier) {
    val tilt = if (spinning) {
        val transition = rememberInfiniteTransition(label = "ufo")
        val wobble by transition.animateFloat(
            initialValue = -14f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "ufoWobble",
        )
        wobble
    } else {
        0f
    }
    Canvas(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { rotationZ = tilt },
    ) {
        val w = size.width
        val h = size.height
        // Dome
        drawArc(
            color = Color(0xFFBFEFFF),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.3f, h * 0.22f),
            size = Size(w * 0.4f, h * 0.44f),
        )
        // Saucer
        drawOval(color = AlienGreen, topLeft = Offset(w * 0.06f, h * 0.4f), size = Size(w * 0.88f, h * 0.26f))
        // Lights
        listOf(0.25f, 0.5f, 0.75f).forEach { x ->
            drawCircle(color = Color.Black, radius = w * 0.035f, center = Offset(w * x, h * 0.53f))
        }
        // Beam
        drawLine(AlienGreen, Offset(w * 0.4f, h * 0.7f), Offset(w * 0.3f, h * 0.95f), 1.5.dp.toPx(), StrokeCap.Round, alpha = 0.6f)
        drawLine(AlienGreen, Offset(w * 0.6f, h * 0.7f), Offset(w * 0.7f, h * 0.95f), 1.5.dp.toPx(), StrokeCap.Round, alpha = 0.6f)
    }
}
// KMK <--
