package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->

/** Eggiku hidden theme: sunny, yolk yellow and toast */
internal object SunnySideUpColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFC23D),
        onPrimary = Color(0xFF3F2C00),
        primaryContainer = Color(0xFF5B4100),
        onPrimaryContainer = Color(0xFFFFDE9E),
        secondary = Color(0xFFE6C38C),
        onSecondary = Color(0xFF3E2D0A),
        secondaryContainer = Color(0xFF56431E),
        onSecondaryContainer = Color(0xFFFFDEA8),
        tertiary = Color(0xFFFF9E6B),
        onTertiary = Color(0xFF4A1C00),
        tertiaryContainer = Color(0xFF6B2E0B),
        onTertiaryContainer = Color(0xFFFFDBCB),
        background = Color(0xFF1C1710),
        onBackground = Color(0xFFF0E1CB),
        surface = Color(0xFF1C1710),
        onSurface = Color(0xFFF0E1CB),
        surfaceVariant = Color(0xFF4E4536),
        onSurfaceVariant = Color(0xFFD3C5AE),
        outline = Color(0xFF9C8F7A),
        surfaceContainerLow = Color(0xFF241E15),
        surfaceContainer = Color(0xFF282219),
        surfaceContainerHigh = Color(0xFF332C22),
        surfaceContainerHighest = Color(0xFF3E372C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFE09A00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDE9E),
        onPrimaryContainer = Color(0xFF261900),
        secondary = Color(0xFF8A6A2E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE9C2),
        onSecondaryContainer = Color(0xFF2B1D00),
        tertiary = Color(0xFFC0582A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBCB),
        onTertiaryContainer = Color(0xFF360F00),
        background = Color(0xFFFFFBF2),
        onBackground = Color(0xFF221B10),
        surface = Color(0xFFFFFBF2),
        onSurface = Color(0xFF221B10),
        surfaceVariant = Color(0xFFF2E3C9),
        onSurfaceVariant = Color(0xFF50452F),
        outline = Color(0xFF82755E),
        surfaceContainerLow = Color(0xFFFFF5E3),
        surfaceContainer = Color(0xFFFCEFD8),
        surfaceContainerHigh = Color(0xFFF7E9CF),
        surfaceContainerHighest = Color(0xFFF1E2C5),
    )
}

/** Eggiku hidden theme: neon synthwave arcade. Always dark. */
internal object MidnightArcadeColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF3EA5),
        onPrimary = Color(0xFF3D0023),
        primaryContainer = Color(0xFF5E0B3F),
        onPrimaryContainer = Color(0xFFFFD8E8),
        secondary = Color(0xFF00E5FF),
        onSecondary = Color(0xFF00363D),
        secondaryContainer = Color(0xFF0A3F4A),
        onSecondaryContainer = Color(0xFFB8F7FF),
        tertiary = Color(0xFFB98CFF),
        onTertiary = Color(0xFF2A0A5E),
        tertiaryContainer = Color(0xFF3F1F7A),
        onTertiaryContainer = Color(0xFFEBDDFF),
        background = Color(0xFF0D0221),
        onBackground = Color(0xFFF3E9FF),
        surface = Color(0xFF0D0221),
        onSurface = Color(0xFFF3E9FF),
        surfaceVariant = Color(0xFF261447),
        onSurfaceVariant = Color(0xFFCDB8F0),
        outline = Color(0xFF6C4FA3),
        outlineVariant = Color(0xFF3A2466),
        surfaceContainerLowest = Color(0xFF07010F),
        surfaceContainerLow = Color(0xFF12052B),
        surfaceContainer = Color(0xFF170933),
        surfaceContainerHigh = Color(0xFF1E0E40),
        surfaceContainerHighest = Color(0xFF26144D),
    )

    override val lightScheme = darkScheme
}
// KMK <--
