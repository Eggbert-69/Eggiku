package eu.kanade.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// KMK -->

/**
 * Eggiku's own navigation icons. They're single-color (the navigation bar tints them), drawn on
 * a 24×24 grid like Material icons. The Alien set replaces all five while Glib Glorb is on.
 */
object EggikuTabIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(d: String, width: Float = 1.6f) {
        addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fill(d: String) {
        addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
    }

    private const val EGG = "M0,-4 C-2.4,-4 -3.3,-0.6 -3.3,1 C-3.3,3 -1.9,4.1 0,4.1 " +
        "C1.9,4.1 3.3,3 3.3,1 C3.3,-0.6 2.4,-4 0,-4 Z"

    private const val SPARKLE_SMALL = "M18.5,2.5 L19.1,4.4 L21,5 L19.1,5.6 L18.5,7.5 L17.9,5.6 L16,5 L17.9,4.4 Z"

    /** A packed bookcase with an egg leaning on its side */
    val Library: ImageVector by lazy {
        icon("EggikuLibrary") {
            addGroup(scaleX = 0.93f, translationX = -0.6f)
            stroke("M3,3 H17 V21 H3 Z")
            stroke("M3,9 H17 M3,15 H17")
            fill(
                "M4.6,4.6 h1.3 v4.4 h-1.3 Z M6.4,5.2 h1.2 v3.8 h-1.2 Z M8.1,4.4 h1.5 v4.6 h-1.5 Z " +
                    "M10.1,5 h1.1 v4 h-1.1 Z M11.8,4.7 h1.4 v4.3 h-1.4 Z",
            )
            fill("M14,9 l1.7,-4.2 l1.1,0.45 l-1.7,4.2 Z")
            fill(
                "M4.6,10.8 h1.4 v4.2 h-1.4 Z M6.5,11.3 h1.1 v3.7 h-1.1 Z M8.1,10.6 h1.6 v4.4 h-1.6 Z " +
                    "M11,12.2 h4.6 v1.3 h-4.6 Z M11.3,13.6 h4.1 v1.4 h-4.1 Z",
            )
            fill(
                "M4.6,16.6 h1.3 v4.4 h-1.3 Z M6.4,17.2 h1.5 v3.8 h-1.5 Z M8.4,16.8 h1.2 v4.2 h-1.2 Z " +
                    "M10.1,17.4 h1.4 v3.6 h-1.4 Z M12,16.6 h1.3 v4.4 h-1.3 Z M13.8,17.6 h1.5 v3.4 h-1.5 Z",
            )
            clearGroup()
            addGroup(rotate = -16f, scaleX = 0.92f, scaleY = 0.92f, translationX = 19.3f, translationY = 17.2f)
            stroke(EGG)
            clearGroup()
        }
    }

    /** A bell with a sparkle */
    val Updates: ImageVector by lazy {
        icon("EggikuUpdates") {
            stroke("M6,16.5 V11 A6,6 0 0 1 16.5,7.3")
            stroke("M18,11 V16.5 H4.5 M18,16.5 H19.5")
            stroke("M10,19.5 A2,2 0 0 0 14,19.5")
            stroke(SPARKLE_SMALL)
        }
    }

    /** A portal door with a sparkle inside */
    val Browse: ImageVector by lazy {
        icon("EggikuBrowse") {
            stroke("M6,21 V9 A6,6 0 0 1 18,9 V21")
            stroke("M4,21 H20")
            stroke("M12,10.5 L12.7,12.8 L15,13.5 L12.7,14.2 L12,16.5 L11.3,14.2 L9,13.5 L11.3,12.8 Z")
        }
    }

    object Alien {
        /** The bookcase, with a little alien peeking out of the top shelf */
        val Library: ImageVector by lazy {
            icon("GlibLibrary") {
                stroke("M3,4 H21 V20 H3 Z M3,12 H21")
                stroke("M7,12 C5.5,9 6.5,6.5 8.5,6.5 C10.5,6.5 11.5,9 10,12")
                fill("M7.6,8.1 A0.7,0.7 0 1 1 7.6,9.5 A0.7,0.7 0 1 1 7.6,8.1 Z")
                fill("M9.4,8.1 A0.7,0.7 0 1 1 9.4,9.5 A0.7,0.7 0 1 1 9.4,8.1 Z")
                stroke("M13,12 V7 M15.5,12 V6 M7,20 V15 M9.5,20 V16 M13,20 L15,15.5")
            }
        }

        /** The bell, with antennae */
        val Updates: ImageVector by lazy {
            icon("GlibUpdates") {
                stroke("M6,16 V11 A6,6 0 0 1 18,11 V16 M4.5,16 H19.5 M10,19 A2,2 0 0 0 14,19")
                stroke("M9.5,5.8 L8,2.8 M14.5,5.8 L16,2.8")
                fill("M7.8,1.6 A0.9,0.9 0 1 1 7.8,3.4 A0.9,0.9 0 1 1 7.8,1.6 Z")
                fill("M16.2,1.6 A0.9,0.9 0 1 1 16.2,3.4 A0.9,0.9 0 1 1 16.2,1.6 Z")
            }
        }

        /** A ringed planet with clock hands */
        val History: ImageVector by lazy {
            icon("GlibHistory") {
                stroke("M12,4 A8,8 0 1 1 12,20 A8,8 0 1 1 12,4 Z")
                stroke("M12,7.5 V12 L15,14")
                addGroup(rotate = -20f, pivotX = 12f, pivotY = 12f)
                stroke("M1,12 A11,3.2 0 1 0 23,12 A11,3.2 0 1 0 1,12 Z", width = 1.3f)
                clearGroup()
            }
        }

        /** A flying saucer with a tractor beam */
        val Browse: ImageVector by lazy {
            icon("GlibBrowse") {
                stroke(
                    "M3,12.5 C3,10.5 7,9 12,9 C17,9 21,10.5 21,12.5 C21,14.5 17,16 12,16 C7,16 3,14.5 3,12.5 Z " +
                        "M8,9.6 C8.5,6.5 10,5 12,5 C14,5 15.5,6.5 16,9.6",
                )
                stroke("M9,16.5 L8.4,18 M7.8,19.5 L7.2,21 M15,16.5 L15.6,18 M16.2,19.5 L16.8,21 M12,17 V18.4 M12,19.8 V21.2")
            }
        }

        /** A little alien */
        val More: ImageVector by lazy {
            icon("GlibMore") {
                stroke("M12,4 C9,4 7.5,7 7.5,9.5 C7.5,12.5 10,15 12,15 C14,15 16.5,12.5 16.5,9.5 C16.5,7 15,4 12,4 Z")
                fill("M9,9 C9.8,8.6 10.8,9.2 11,10.4 C10,10.6 9.2,10.2 9,9 Z")
                fill("M15,9 C14.2,8.6 13.2,9.2 13,10.4 C14,10.6 14.8,10.2 15,9 Z")
                stroke("M12,15 V20 M8,20 H16")
            }
        }
    }
}
// KMK <--
