package eu.kanade.presentation.browse.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// KMK -->

/**
 * Eggiku: icons for the 18+ button in Browse.
 * - [Intact]: a whole egg, drawn as an outline that takes the toolbar's color. 18+ is hidden.
 * - [Cracked]: a cracked egg with the yolk spilling out, in its own colors. 18+ is shown.
 */
object EggikuEggIcons {

    val Intact: ImageVector by lazy {
        ImageVector.Builder(
            name = "EggikuEggIntact",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M12,2.5 C8.4,2.5 5.2,8.4 5.2,13.4 C5.2,17.8 8.2,21.3 12,21.3 " +
                        "C15.8,21.3 18.8,17.8 18.8,13.4 C18.8,8.4 15.6,2.5 12,2.5 Z",
                ),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Little shine
            addPath(
                pathData = addPathNodes("M9.2,8.2 C9.8,7 10.6,6.1 11.4,5.7"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
            )
        }.build()
    }

    private val Shell = Color(0xFFFFF3E0)
    private val ShellEdge = Color(0xFF6D4C41)
    private val Yolk = Color(0xFFFFB300)
    private val YolkEdge = Color(0xFFE65100)
    private val YolkDeep = Color(0xFFFFA000)
    private val White = Color(0xFFFFFFFF)
    private val WhiteEdge = Color(0xFFBDBDBD)
    private val Shine = Color(0xFFFFF8E1)

    val Cracked: ImageVector by lazy {
        ImageVector.Builder(
            name = "EggikuEggCracked",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Egg white puddle
            addPath(
                pathData = addPathNodes(
                    "M2.6,21.2 C2.6,19.9 5,19.4 7.2,19.6 C9,19.8 10.4,19.2 12.2,19.4 " +
                        "C14.6,19.6 15.6,20.5 15.2,21.3 C14.8,22.1 12.6,22.2 10.6,22 " +
                        "C8.4,21.8 6.6,22.4 4.6,22.3 C3.4,22.2 2.6,21.9 2.6,21.2 Z",
                ),
                fill = SolidColor(White),
                stroke = SolidColor(WhiteEdge),
                strokeLineWidth = 0.6f,
            )
            // Yolk puddle
            addPath(
                pathData = addPathNodes(
                    "M4.6,20.9 C4.6,20.1 6,19.8 7.2,20 C8.4,20.2 8.6,21 7.8,21.4 C7,21.8 4.6,21.7 4.6,20.9 Z",
                ),
                fill = SolidColor(YolkDeep),
            )
            // Bottom half of the shell
            addPath(
                pathData = addPathNodes(
                    "M8.2,10.6 L9.6,12 L11.2,10.4 L12.8,12 L14.4,10.4 L15.9,11.9 L17.4,10.5 " +
                        "C18.4,11.9 19,13.4 19,14.9 C19,18.3 16.2,20.8 12.6,20.8 " +
                        "C9,20.8 6.6,18.4 6.6,15 C6.6,13.4 7.2,11.9 8.2,10.6 Z",
                ),
                fill = SolidColor(Shell),
                stroke = SolidColor(ShellEdge),
                strokeLineWidth = 0.9f,
                strokeLineJoin = StrokeJoin.Round,
            )
            // Yolk spilling over the rim and down the side
            addPath(
                pathData = addPathNodes(
                    "M9.4,11.6 C10.6,10.9 13.4,11 14.4,11.9 C15,12.5 14,13.2 12.8,13.2 " +
                        "C11.8,13.2 11,13.4 10.2,13.7 C8.9,14.2 8.2,15.2 7.6,16.6 " +
                        "C7.1,17.8 7,19 6.6,19.8 C6.3,20.4 5.6,20.3 5.6,19.5 " +
                        "C5.6,18.2 6.1,16.4 6.8,14.8 C7.4,13.3 8.2,12.3 9.4,11.6 Z",
                ),
                fill = SolidColor(Yolk),
                stroke = SolidColor(YolkEdge),
                strokeLineWidth = 0.5f,
            )
            // Shine on the yolk
            addPath(
                pathData = addPathNodes("M10.4,11.8 C11.2,11.5 12.2,11.5 12.8,11.7"),
                stroke = SolidColor(Shine),
                strokeLineWidth = 0.6f,
                strokeLineCap = StrokeCap.Round,
            )
            // Top of the shell, knocked off
            addPath(
                pathData = addPathNodes(
                    "M13.2,2.6 C16.2,2.2 18.9,4.6 19.9,7.6 L18.4,8.3 L17.2,6.8 L15.7,8.1 " +
                        "L14.5,6.4 L13,7.4 C12.2,5.8 12,3.5 13.2,2.6 Z",
                ),
                fill = SolidColor(Shell),
                stroke = SolidColor(ShellEdge),
                strokeLineWidth = 0.9f,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}
// KMK <--
