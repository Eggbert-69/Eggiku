package eu.kanade.presentation.reader.music

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.music.EggikuMusic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.GlassDefaults
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.IconButton
import tachiyomi.presentation.core.components.material.glass
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

/** Where the reader's mini player is: hidden, the full capsule, or shrunk to the music note */
enum class MiniPlayerForm { HIDDEN, EXPANDED, COLLAPSED }

private val EnterSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 500f)
private val NoBounceSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
private val PressSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)

/** Capsule: 1.00 → 1.035 → 0.985 → 1.00 over ~220 ms */
private val CapsuleJiggle = keyframes<Float> {
    durationMillis = 220
    1f at 0
    1.035f at 70
    0.985f at 150
    1f at 220
}

/** Note: 1.00 → 1.06 → 1.00 */
private val NoteJiggle = keyframes<Float> {
    durationMillis = 180
    1f at 0
    1.06f at 80
    1f at 180
}

@Stable
class MiniPlayerAnimationSettings(
    val reduceMotion: Boolean,
    val bounce: Boolean,
)

@Composable
fun rememberMiniPlayerAnimationSettings(bounce: Boolean): MiniPlayerAnimationSettings {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    return remember(reduceMotion, bounce) { MiniPlayerAnimationSettings(reduceMotion, bounce) }
}

private fun View.tick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        },
    )
}

/**
 * Eggiku: the reader's floating music player. Everything moves through graphicsLayer
 * (translation, scale, alpha) so animating never re-lays out or recomposes per frame.
 */
@Composable
fun EggikuMiniPlayer(
    nowPlaying: EggikuMusic.NowPlaying?,
    form: MiniPlayerForm,
    playlistName: String?,
    animation: MiniPlayerAnimationSettings,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val slideDistance = with(density) { 96.dp.toPx() }
    val edgeShift = with(density) { 140.dp.toPx() }

    // 0 = gone, 1 = in place. Springs may briefly overshoot past 1.
    val capsule = remember { Animatable(0f) }
    val note = remember { Animatable(0f) }
    val capsuleScale = remember { Animatable(1f) }
    val noteScale = remember { Animatable(1f) }
    // Moves the capsule toward the note while collapsing (0 = centred, 1 = at the edge)
    val towardEdge = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    var lastForm by remember { mutableStateOf(MiniPlayerForm.HIDDEN) }
    val scope = rememberCoroutineScope()

    // Only nowPlaying != null can show anything
    val target = if (nowPlaying == null) MiniPlayerForm.HIDDEN else form

    LaunchedEffect(target) {
        val from = lastForm
        lastForm = target
        if (animation.reduceMotion) {
            // System animations are off: just show or hide
            capsule.snapTo(if (target == MiniPlayerForm.EXPANDED) 1f else 0f)
            note.snapTo(if (target == MiniPlayerForm.COLLAPSED) 1f else 0f)
            towardEdge.snapTo(0f)
            drag.snapTo(0f)
            capsuleScale.snapTo(1f)
            noteScale.snapTo(1f)
            return@LaunchedEffect
        }
        when (target) {
            MiniPlayerForm.EXPANDED -> {
                if (from == MiniPlayerForm.COLLAPSED) {
                    // Reverse of collapsing: the note goes, the capsule grows back out of the edge
                    launch { note.animateTo(0f, tween(120, easing = EaseIn)) }
                    towardEdge.snapTo(1f)
                    launch { towardEdge.animateTo(0f, NoBounceSpring) }
                } else {
                    towardEdge.snapTo(0f)
                }
                drag.snapTo(0f)
                capsule.animateTo(1f, EnterSpring)
                if (animation.bounce) {
                    view.tick()
                    capsuleScale.animateTo(1f, CapsuleJiggle)
                }
            }
            MiniPlayerForm.COLLAPSED -> {
                // Shrink toward the edge, then pop the note in
                launch { towardEdge.animateTo(1f, tween(200)) }
                capsule.animateTo(0f, tween(200))
                drag.snapTo(0f)
                note.animateTo(1f, EnterSpring)
                if (animation.bounce) {
                    view.tick()
                    noteScale.animateTo(1f, NoteJiggle)
                }
            }
            MiniPlayerForm.HIDDEN -> {
                // Fast and clean: no bounce on the way out
                launch { note.animateTo(0f, tween(160, easing = EaseIn)) }
                capsule.animateTo(0f, tween(160, easing = EaseIn))
                drag.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Keep the capsule composed only while it's on screen or animating
        if (nowPlaying != null && (target == MiniPlayerForm.EXPANDED || capsule.isRunning || capsule.value > 0.001f)) {
            MiniPlayerCapsule(
                nowPlaying = nowPlaying,
                playlistName = playlistName,
                ticking = target == MiniPlayerForm.EXPANDED,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .graphicsLayer {
                        val p = capsule.value
                        val collapse = towardEdge.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * slideDistance * (1f - collapse) + drag.value
                        translationX = collapse * edgeShift
                        val shrink = 1f - collapse * 0.7f
                        scaleX = capsuleScale.value * shrink
                        scaleY = capsuleScale.value * shrink
                    }
                    .pointerInput(Unit) {
                        // Swipe down to shrink to the music note
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (drag.value > 48.dp.toPx()) {
                                    onCollapse()
                                } else {
                                    scope.launch { drag.animateTo(0f, EnterSpring) }
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, EnterSpring) } },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                        }
                    },
            )
        }

        if (nowPlaying != null && (target == MiniPlayerForm.COLLAPSED || note.isRunning || note.value > 0.001f)) {
            MusicNoteButton(
                onClick = onExpand,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        val p = note.value
                        alpha = p.coerceIn(0f, 1f)
                        val s = p.coerceAtLeast(0f) * noteScale.value
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

@Composable
private fun MiniPlayerCapsule(
    nowPlaying: EggikuMusic.NowPlaying,
    playlistName: String?,
    ticking: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(0f) }

    // Progress: tween width changes, snap when the song changes, and stop ticking when hidden
    LaunchedEffect(nowPlaying.trackKey) {
        progress.snapTo(fractionOf(nowPlaying))
    }
    LaunchedEffect(nowPlaying, ticking) {
        if (!ticking) return@LaunchedEffect
        while (true) {
            progress.animateTo(fractionOf(nowPlaying), tween(250, easing = LinearEasing))
            if (!nowPlaying.isPlaying) break
            delay(1_000)
        }
    }

    Column(
        modifier = modifier
            .glass(shape = GlassDefaults.BarShape)
            .clip(GlassDefaults.BarShape),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = remember(nowPlaying.art) { nowPlaying.art?.asImageBitmap() }
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = nowPlaying.title.ifBlank { nowPlaying.appName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val source = playlistName ?: nowPlaying.queueTitle ?: nowPlaying.artist
                Text(
                    text = if (source.isBlank()) nowPlaying.appName else "${nowPlaying.appName} · $source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PressableIconButton(onClick = EggikuMusic::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(KMR.strings.music_previous))
            }
            PressableIconButton(onClick = EggikuMusic::togglePlayPause) {
                AnimatedContent(
                    targetState = nowPlaying.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.7f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f))
                    },
                    label = "playPause",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) KMR.strings.music_pause else KMR.strings.music_play,
                        ),
                    )
                }
            }
            PressableIconButton(onClick = EggikuMusic::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(KMR.strings.music_next))
            }
        }
        // Thin progress line, drawn (not laid out) so it never recomposes as it moves
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .padding(horizontal = 20.dp)
                .drawBehind {
                    drawRect(color = primary.copy(alpha = 0.18f))
                    drawRect(
                        color = primary,
                        size = size.copy(width = size.width * progress.value.coerceIn(0f, 1f)),
                    )
                },
        )
        Box(modifier = Modifier.height(4.dp))
    }
}

private fun fractionOf(nowPlaying: EggikuMusic.NowPlaying): Float {
    if (nowPlaying.durationMs <= 0L) return 0f
    return (nowPlaying.positionNow().toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
}

/** Every button shrinks to 0.92 while pressed and springs back */
@Composable
private fun PressableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, PressSpring, label = "press")
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content,
    )
}

@Composable
private fun MusicNoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, PressSpring, label = "press")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(48.dp)
            .glass(shape = CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, interactionSource = interaction) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = stringResource(KMR.strings.music_show_player),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
// KMK <--
