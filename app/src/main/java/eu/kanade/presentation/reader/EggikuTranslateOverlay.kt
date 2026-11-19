package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.eggiku.EggikuTranslate
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

// KMK -->
/**
 * Translations laid over the page, each in a white bubble where the original text was.
 * Tap anywhere to close; hold a bubble to see the original text.
 */
@Composable
fun EggikuTranslateOverlay() {
    val state by EggikuTranslate.state.collectAsState()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    AnimatedVisibility(visible = state !is EggikuTranslate.State.Idle, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    EggikuTranslate.close()
                },
        ) {
            when (val s = state) {
                is EggikuTranslate.State.Done -> {
                    if (s.blocks.isEmpty()) {
                        Status(stringResource(KMR.strings.translate_nothing_found), working = false)
                    }
                    s.blocks.forEach { block ->
                        var showOriginal by remember(block) { mutableStateOf(false) }
                        val width = with(density) { block.rect.width().toDp() }
                        val height = with(density) { block.rect.height().toDp() }
                        Surface(
                            color = Color.White,
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (block.rect.left - origin.x).roundToInt(),
                                        (block.rect.top - origin.y).roundToInt(),
                                    )
                                }
                                .size(width = width.coerceAtLeast(64.dp), height = height.coerceAtLeast(28.dp))
                                .clickable { showOriginal = !showOriginal },
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(3.dp)) {
                                FittedText(if (showOriginal) block.original else block.translated, maxHeight = height)
                            }
                        }
                    }
                }
                is EggikuTranslate.State.Working -> Status(
                    stringResource(if (s.downloading) KMR.strings.translate_downloading else KMR.strings.translate_working),
                    working = true,
                )
                is EggikuTranslate.State.Failed -> Status(stringResource(KMR.strings.translate_failed, s.message.orEmpty()), working = false)
                EggikuTranslate.State.Idle -> Unit
            }
        }
    }
}

/** Shrinks the text until it fits its bubble */
@Composable
private fun FittedText(text: String, maxHeight: androidx.compose.ui.unit.Dp) {
    var size by remember(text) { mutableStateOf(15f) }
    Text(
        text = text,
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        textAlign = TextAlign.Center,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size > 8f) size -= 1f
        },
        maxLines = if (maxHeight < 40.dp) 2 else Int.MAX_VALUE,
    )
}

@Composable
private fun Status(text: String, working: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(top = 72.dp)
                .background(Color.Transparent),
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text(text, modifier = Modifier.padding(start = if (working) 10.dp else 0.dp))
            }
        }
    }
}
// KMK <--
