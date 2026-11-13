package eu.kanade.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp

// KMK -->

/** Eggiku's egg shape, the same one as the logo: 36 wide by 46 tall */
private fun eggPath(topLeft: Offset, size: Size): Path {
    val sx = size.width / 36f
    val sy = size.height / 46f
    fun x(v: Float) = topLeft.x + (v - 22f) * sx
    fun y(v: Float) = topLeft.y + (v - 18f) * sy
    return Path().apply {
        moveTo(x(40f), y(18f))
        cubicTo(x(50f), y(18f), x(58f), y(33f), x(58f), y(44f))
        cubicTo(x(58f), y(56f), x(50f), y(64f), x(40f), y(64f))
        cubicTo(x(30f), y(64f), x(22f), y(56f), x(22f), y(44f))
        cubicTo(x(22f), y(33f), x(30f), y(18f), x(40f), y(18f))
        close()
    }
}

/** Shell colour for an egg timer of this many minutes */
fun eggikuEggColor(minutes: Int): Color = when {
    minutes >= 60 -> Color(0xFF9FD8E8)
    minutes >= 45 -> Color(0xFFD9A877)
    minutes >= 30 -> Color(0xFFFFD27A)
    minutes >= 20 -> Color(0xFFFFEBC4)
    else -> Color(0xFFFFFBF2)
}

private val Ink = Color(0xFF2B2233)

/** A whole egg in the box at [topLeft]/[size], tinted from raw white towards [cooked] by [progress] */
fun DrawScope.drawEggikuEgg(topLeft: Offset, size: Size, progress: Float, cooked: Color) {
    val path = eggPath(topLeft, size)
    drawPath(path, lerp(Color(0xFFFFFBF2), cooked, progress.coerceIn(0f, 1f)))
    clipPath(path) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.08f),
            radius = size.width * 0.75f,
            center = Offset(topLeft.x + size.width * 1.05f, topLeft.y + size.height * 0.75f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = size.width * 0.09f,
            center = Offset(topLeft.x + size.width * 0.33f, topLeft.y + size.height * 0.28f),
        )
    }
    drawPath(path, Ink.copy(alpha = 0.18f), style = Stroke(width = size.width * 0.03f))
}

/** A hatched egg: the bottom half of the shell with a little chick peeking out */
fun DrawScope.drawEggikuHatched(topLeft: Offset, size: Size, shell: Color) {
    val w = size.width
    val h = size.height
    val cx = topLeft.x + w / 2f
    // Chick
    val headRadius = w * 0.3f
    val headCenter = Offset(cx, topLeft.y + h * 0.5f)
    drawCircle(Color(0xFFFFD84D), headRadius, headCenter)
    drawCircle(Ink, w * 0.04f, Offset(cx - w * 0.1f, headCenter.y - h * 0.02f))
    drawCircle(Ink, w * 0.04f, Offset(cx + w * 0.1f, headCenter.y - h * 0.02f))
    drawPath(
        Path().apply {
            moveTo(cx - w * 0.06f, headCenter.y + h * 0.04f)
            lineTo(cx + w * 0.06f, headCenter.y + h * 0.04f)
            lineTo(cx, headCenter.y + h * 0.1f)
            close()
        },
        Color(0xFFFF9F43),
    )
    // Bottom shell with a zigzag top
    val full = eggPath(topLeft, size)
    val crackY = topLeft.y + h * 0.58f
    val zig = Path().apply {
        moveTo(topLeft.x - w, topLeft.y + h * 2f)
        lineTo(topLeft.x - w, crackY)
        val teeth = 6
        for (i in 0..teeth) {
            val x = topLeft.x + w * i / teeth.toFloat()
            lineTo(x, if (i % 2 == 0) crackY else crackY - h * 0.08f)
        }
        lineTo(topLeft.x + w * 2f, crackY)
        lineTo(topLeft.x + w * 2f, topLeft.y + h * 2f)
        close()
    }
    clipPath(zig) {
        drawPath(full, shell)
        drawPath(full, Ink.copy(alpha = 0.18f), style = Stroke(width = w * 0.03f))
    }
}
// KMK <--
