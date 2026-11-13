package tachiyomi.presentation.core.components.material

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

// KMK -->

/**
 * Eggiku glass look: translucent, blurred surfaces tinted with the active theme's colors.
 */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/**
 * Blur source of the nearest [Scaffold]; its content is what glass surfaces blur.
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Extra space at the bottom that a [Scaffold] must keep clear, e.g. for a floating navigation bar
 * drawn on top of it by a parent.
 */
val LocalScaffoldExtraBottomInset = compositionLocalOf { 0.dp }

object GlassDefaults {
    val BarShape = RoundedCornerShape(32.dp)
    val BlurRadius: Dp = 24.dp
}

/**
 * Draws a glass surface behind the content: blurred backdrop, theme tint, soft sheen and a light edge.
 * Falls back to a plain tinted background when glass is turned off.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RectangleShape,
    outlined: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier {
    if (!LocalGlassEnabled.current) {
        return this.background(tint, shape)
    }

    val hazeState = LocalGlassHazeState.current
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    val backdrop = if (hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = scheme.background,
                    tint = HazeDefaults.tint(tint.copy(alpha = 0.8f)),
                    blurRadius = GlassDefaults.BlurRadius,
                ),
            )
    } else {
        this
            .clip(shape)
            .background(tint.copy(alpha = 0.92f))
    }

    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.08f else 0.30f),
            Color.Transparent,
        ),
    )
    val edgeTop = Color.White.copy(alpha = if (isDark) 0.26f else 0.70f)
    val edgeBottom = scheme.outline.copy(alpha = 0.20f)

    return if (outlined) {
        backdrop
            .background(sheen)
            .border(0.75.dp, Brush.verticalGradient(listOf(edgeTop, edgeBottom)), shape)
    } else {
        backdrop
            .background(sheen)
            .drawWithContent {
                drawContent()
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = edgeBottom,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
    }
}
// KMK <--

// KMK -->
/**
 * Eggiku: frosts everything behind a dialog or sheet window (Android 12 and newer). Call it from
 * inside the dialog's content; does nothing when the glass look is off.
 */
@Composable
fun EggikuBlurBehind(radiusPx: Int = 60) {
    if (!LocalGlassEnabled.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && window.windowManager.isCrossWindowBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
        }
        onDispose {}
    }
}
// KMK <--
