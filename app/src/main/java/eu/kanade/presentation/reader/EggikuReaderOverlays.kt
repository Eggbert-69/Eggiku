package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.stats.EggikuStatsFormat
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuPanelPeek
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/** Eggiku: which way the "end of chapter" glow should sit */
enum class EggikuGlowEdge { Right, Left, Bottom }

/** A soft pulsing glow on the edge of the screen on the last page of a chapter */
@Composable
fun EggikuEdgeGlow(visible: Boolean, edge: EggikuGlowEdge) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
        val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow-alpha",
        )
        val color = MaterialTheme.colorScheme.primary
        Box(modifier = Modifier.fillMaxSize()) {
            val (align, modifier, brush) = when (edge) {
                EggikuGlowEdge.Right -> Triple(
                    Alignment.CenterEnd,
                    Modifier.fillMaxHeight().width(28.dp),
                    Brush.horizontalGradient(listOf(Color.Transparent, color)),
                )
                EggikuGlowEdge.Left -> Triple(
                    Alignment.CenterStart,
                    Modifier.fillMaxHeight().width(28.dp),
                    Brush.horizontalGradient(listOf(color, Color.Transparent)),
                )
                EggikuGlowEdge.Bottom -> Triple(
                    Alignment.BottomCenter,
                    Modifier.fillMaxWidth().height(28.dp),
                    Brush.verticalGradient(listOf(Color.Transparent, color)),
                )
            }
            Box(
                modifier = modifier
                    .align(align)
                    .alpha(pulse)
                    .background(brush),
            )
        }
    }
}

/** The held panel, popped up over a dimmed page */
@Composable
fun EggikuPeekOverlay() {
    val bitmap by EggikuPanelPeek.showing.collectAsState()
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    AnimatedVisibility(
        visible = image != null,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** Shown on the "Finished / Next" page between chapters */
@Composable
fun EggikuChapterEndCard(finished: Chapter?) {
    val activity = LocalContext.current as? ReaderActivity ?: return
    finished ?: return
    val seconds = remember(finished.id) { activity.viewModel.eggikuChapterSeconds() }
    val streak = remember { EggikuReadingLog.streak() }
    val today = remember { EggikuReadingLog.all()[EggikuReadingLog.today()]?.chapters ?: 0 }
    var bookmarked by remember(finished.id) { mutableStateOf(finished.bookmark) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(top = 24.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (seconds > 0) {
                val (h, m) = EggikuStatsFormat.hoursAndMinutes(seconds)
                Text(
                    "⏱️ " + stringResource(KMR.strings.chapter_end_time, stringResource(KMR.strings.stats_duration, h, m)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (streak.current > 0) {
                Text(
                    "🔥 " + stringResource(KMR.strings.chapter_end_streak, streak.current),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (today > 0) {
                Text(
                    "📖 " + stringResource(KMR.strings.chapter_end_today, today),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        bookmarked = !bookmarked
                        activity.viewModel.eggikuSetBookmark(finished.id, bookmarked)
                    },
                ) {
                    Icon(
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                    )
                    Text(
                        stringResource(if (bookmarked) KMR.strings.chapter_end_bookmarked else KMR.strings.chapter_end_bookmark),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
// KMK <--
