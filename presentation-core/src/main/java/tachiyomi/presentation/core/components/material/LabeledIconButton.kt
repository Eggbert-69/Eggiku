package tachiyomi.presentation.core.components.material

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

// KMK -->

/**
 * Where an [Icon] reports its description so the enclosing [IconButton] can show it as a label.
 */
private val LocalIconLabelSink = compositionLocalOf<MutableState<String?>?> { null }

/**
 * Drop-in replacement for the Material 3 icon button that shows what the button does,
 * below the icon, when it is pressed and held. The label is the description of the [Icon] inside it.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val label = remember { mutableStateOf<String?>(null) }
    // Squish a little while pressed, then spring back
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "iconButtonPress",
    )
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            label.value?.let {
                PlainTooltip {
                    Text(it)
                }
            }
        },
        state = rememberTooltipState(),
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        focusable = false,
    ) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            interactionSource = source,
        ) {
            CompositionLocalProvider(LocalIconLabelSink provides label, content = content)
        }
    }
}

@Composable
private fun ReportIconLabel(contentDescription: String?) {
    val sink = LocalIconLabelSink.current
    if (sink != null && !contentDescription.isNullOrBlank()) {
        SideEffect { sink.value = contentDescription }
    }
}

/**
 * Drop-in replacement for the Material 3 icon that reports its description to an enclosing [IconButton].
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
// KMK <--
