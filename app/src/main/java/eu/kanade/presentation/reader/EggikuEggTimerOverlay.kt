package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import eu.kanade.presentation.components.drawEggikuEgg
import eu.kanade.presentation.components.drawEggikuHatched
import eu.kanade.presentation.components.eggikuEggColor
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuEggTimer
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuTilt
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

private fun <T> jiggle() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

/** The cooking egg in the corner, the session picker and the hatch card */
@Composable
fun BoxScope.EggikuEggTimerOverlay(menuVisible: Boolean) {
    val session by EggikuEggTimer.session.collectAsState()
    val pickerOpen by EggikuEggTimer.pickerOpen.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val running = session?.let { !it.hatched } == true

    // Only time with the reader open and in front counts
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1_000)
                EggikuEggTimer.tick()
            }
        }
    }

    val current = session
    AnimatedVisibility(
        visible = current != null && !current.hatched && !menuVisible,
        enter = scaleIn(jiggle()) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        if (current != null) CookingEgg(current)
    }

    if (current?.hatched == true) {
        LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        HatchCard(current, Modifier.align(Alignment.Center))
    }

    if (pickerOpen) {
        EggTimerPicker(current, onDismiss = { EggikuEggTimer.pickerOpen.value = false })
    }
}

@Composable
private fun CookingEgg(session: EggikuEggTimer.Session) {
    var showLeft by remember { mutableStateOf(false) }
    val nearlyDone = session.progress > 0.9f
    val wobble by rememberInfiniteTransition(label = "wobble").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (nearlyDone) 260 else 1400), RepeatMode.Reverse),
        label = "wobble-angle",
    )
    val ringTrack = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val ring = MaterialTheme.colorScheme.primary
    val cooked = eggikuEggColor(session.minutes)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(visible = showLeft, enter = fadeIn() + scaleIn(jiggle()), exit = fadeOut()) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)) {
                val minutesLeft = (session.secondsLeft + 59) / 60
                Text(
                    text = pluralStringResource(KMR.plurals.egg_timer_minutes_left, minutesLeft, minutesLeft),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .size(48.dp)
                .clickable { showLeft = !showLeft }
                .graphicsLayer { rotationZ = wobble * if (nearlyDone) 9f else 3f },
        ) {
            val stroke = 3.dp.toPx()
            drawCircle(ringTrack, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * session.progress,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val eggHeight = size.height * 0.62f
            val eggWidth = eggHeight * 36f / 46f
            drawEggikuEgg(
                topLeft = Offset((size.width - eggWidth) / 2f, (size.height - eggHeight) / 2f),
                size = Size(eggWidth, eggHeight),
                progress = session.progress,
                cooked = cooked,
            )
        }
    }
}

@Composable
private fun HatchCard(session: EggikuEggTimer.Session, modifier: Modifier) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, jiggle()) }
    Surface(
        modifier = modifier
            .padding(32.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shell = eggikuEggColor(session.minutes)
            Canvas(modifier = Modifier.size(96.dp)) {
                val h = size.height * 0.9f
                val w = h * 36f / 46f
                drawEggikuHatched(Offset((size.width - w) / 2f, size.height - h), Size(w, h), shell)
            }
            Text(
                text = stringResource(KMR.strings.egg_timer_hatched),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(KMR.plurals.egg_timer_hatched_body, session.minutes, session.minutes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { EggikuEggTimer.start(session.minutes) }) {
                    Text(stringResource(KMR.strings.egg_timer_again))
                }
                FilledTonalButton(onClick = EggikuEggTimer::dismissHatched) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
        }
    }
}

@Composable
private fun EggTimerPicker(session: EggikuEggTimer.Session?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.egg_timer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session != null && !session.hatched) {
                    val minutesLeft = (session.secondsLeft + 59) / 60
                    Text(pluralStringResource(KMR.plurals.egg_timer_minutes_left, minutesLeft, minutesLeft))
                } else {
                    Text(
                        text = stringResource(KMR.strings.egg_timer_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EggikuEggTimer.LENGTHS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { minutes ->
                                FilledTonalButton(
                                    onClick = {
                                        EggikuEggTimer.start(minutes)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(KMR.strings.egg_timer_length, minutes))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (session != null && !session.hatched) {
                TextButton(
                    onClick = {
                        EggikuEggTimer.cancel()
                        onDismiss()
                    },
                ) { Text(stringResource(KMR.strings.egg_timer_cancel)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_close)) }
        },
    )
}

/** A small reminder that tilt to scroll is on */
@Composable
fun BoxScope.EggikuTiltIndicator(menuVisible: Boolean, isWebtoon: Boolean) {
    val active by EggikuTilt.active.collectAsState()
    AnimatedVisibility(
        visible = active && isWebtoon && !menuVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        val sway by rememberInfiniteTransition(label = "tilt").animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "tilt-angle",
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { alpha = 0.55f },
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(
                    imageVector = Icons.Outlined.ScreenRotation,
                    contentDescription = stringResource(KMR.strings.tilt_scroll),
                    modifier = Modifier
                        .padding(5.dp)
                        .graphicsLayer { rotationX = sway },
                )
            }
        }
    }
}
// KMK <--
