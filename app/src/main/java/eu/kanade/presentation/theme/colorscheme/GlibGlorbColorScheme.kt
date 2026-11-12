package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->

/**
 * Eggiku's hidden Glib Glorb theme: pure black with alien green. It's always dark,
 * so the "light" scheme is the same as the dark one.
 */
internal object GlibGlorbColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7CFF6B),
        onPrimary = Color(0xFF002204),
        primaryContainer = Color(0xFF12420F),
        onPrimaryContainer = Color(0xFFB6FFAB),
        secondary = Color(0xFF6BE3C8),
        onSecondary = Color(0xFF00201A),
        secondaryContainer = Color(0xFF0E3A30),
        onSecondaryContainer = Color(0xFFA8F5E3),
        tertiary = Color(0xFFC79BFF),
        onTertiary = Color(0xFF2A0A52),
        tertiaryContainer = Color(0xFF3B1B6B),
        onTertiaryContainer = Color(0xFFE7D4FF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE4FFE0),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE4FFE0),
        surfaceVariant = Color(0xFF0B160A),
        onSurfaceVariant = Color(0xFF9FDC95),
        surfaceTint = Color(0xFF7CFF6B),
        inverseSurface = Color(0xFFE4FFE0),
        inverseOnSurface = Color(0xFF0B160A),
        outline = Color(0xFF2E5A2A),
        outlineVariant = Color(0xFF1B3318),
        inversePrimary = Color(0xFF1F7A16),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF142412),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF050B05),
        surfaceContainer = Color(0xFF0A140A),
        surfaceContainerHigh = Color(0xFF0E1B0D),
        surfaceContainerHighest = Color(0xFF142412),
    )

    override val lightScheme = darkScheme
}
// KMK <--
