package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuChapterTranslator
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuChapterTranslator.Progress
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuChapterTranslator.Stage
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuTranslate
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/**
 * Shown while a whole chapter is being translated: a spinning egg, how far along it is, and Skip
 * to start reading straight away with quick translations that upgrade when the chapter is done.
 */
@Composable
fun EggikuChapterTranslatingScreen() {
    val active by EggikuTranslate.active.collectAsState()
    val progress by EggikuChapterTranslator.progress.collectAsState()
    val skipped by EggikuChapterTranslator.skipped.collectAsState()
    val working = progress as? Progress.Working
    val visible = active && working != null && working.chapterId !in skipped

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // Keeps the last state on screen while fading out
        val shown = working ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Taps don't fall through to the reader underneath
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                SpinningEgg()
                Text(
                    stringResource(KMR.strings.translate_chapter_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = when (shown.stage) {
                        Stage.Loading -> stringResource(KMR.strings.translate_chapter_loading)
                        Stage.Reading -> stringResource(KMR.strings.translate_chapter_reading, shown.done + 1, shown.total)
                        Stage.Translating -> when (shown.engine) {
                            "gemini" -> stringResource(KMR.strings.translate_chapter_gemini)
                            "nano" -> stringResource(KMR.strings.translate_chapter_nano)
                            else -> stringResource(KMR.strings.translate_chapter_offline)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (shown.stage == Stage.Reading && shown.total > 0) {
                    LinearProgressIndicator(
                        progress = { (shown.done + 1f) / shown.total },
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
                }
                OutlinedButton(onClick = { EggikuChapterTranslator.skip(shown.chapterId) }) {
                    Text(stringResource(KMR.strings.translate_chapter_skip))
                }
                Text(
                    stringResource(KMR.strings.translate_chapter_skip_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** An egg that spins, then settles with a little wobble */
@Composable
private fun SpinningEgg() {
    val transition = rememberInfiniteTransition(label = "egg")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0 using FastOutSlowInEasing
                360f at 1100 using LinearEasing
                372f at 1300
                354f at 1450
                364f at 1600
                360f at 1800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Icon(
        imageVector = Icons.Filled.Egg,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                rotationZ = rotation
                translationY = bob * density
            },
    )
}
// KMK <--
