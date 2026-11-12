package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Tokyo Night theme (Eggiku)
 * Palette: https://github.com/folke/tokyonight.nvim
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object TokyoNightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF1A1B26),
        primaryContainer = Color(0xFF394669),
        onPrimaryContainer = Color(0xFF9BB9F9),
        inversePrimary = Color(0xFF496194),
        secondary = Color(0xFFBB9AF7), // Unread badge
        onSecondary = Color(0xFF1A1B26), // Unread badge text
        secondaryContainer = Color(0xFF4E4469), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFCCB3F9), // Navigation bar selector icon
        tertiary = Color(0xFF9ECE6A), // Downloaded badge
        onTertiary = Color(0xFF1A1B26), // Downloaded badge text
        tertiaryContainer = Color(0xFF44543C),
        onTertiaryContainer = Color(0xFFB6DA8F),
        background = Color(0xFF1A1B26),
        onBackground = Color(0xFFC0CAF5),
        surface = Color(0xFF1A1B26),
        onSurface = Color(0xFFC0CAF5),
        surfaceVariant = Color(0xFF262734), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFA2AAD0),
        surfaceTint = Color(0xFF7AA2F7),
        inverseSurface = Color(0xFFC0CAF5),
        inverseOnSurface = Color(0xFF1A1B26),
        outline = Color(0xFF6D728E),
        outlineVariant = Color(0xFF484C60),
        error = Color(0xFFF7768E),
        onError = Color(0xFF1A1B26),
        errorContainer = Color(0xFF613847),
        onErrorContainer = Color(0xFFF998AA),
        surfaceContainerLowest = Color(0xFF15161F),
        surfaceContainerLow = Color(0xFF21222E),
        surfaceContainer = Color(0xFF262734), // Navigation bar background
        surfaceContainerHigh = Color(0xFF2B2C3B),
        surfaceContainerHighest = Color(0xFF313443),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF2E7DE9),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFBACCE7),
        onPrimaryContainer = Color(0xFF1E529A),
        inversePrimary = Color(0xFF82B1F2),
        secondary = Color(0xFF9854F1), // Unread badge
        onSecondary = Color(0xFF000000), // Unread badge text
        secondaryContainer = Color(0xFFD1C3E9), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF693AA6), // Navigation bar selector icon
        tertiary = Color(0xFF587539), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFC3CAC1),
        onTertiaryContainer = Color(0xFF42582B),
        background = Color(0xFFE1E2E7),
        onBackground = Color(0xFF28458A),
        surface = Color(0xFFE1E2E7),
        onSurface = Color(0xFF28458A),
        surfaceVariant = Color(0xFFD6D9E1), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF465D94),
        surfaceTint = Color(0xFF2E7DE9),
        inverseSurface = Color(0xFF28458A),
        inverseOnSurface = Color(0xFFE1E2E7),
        outline = Color(0xFF6F7C9B),
        outlineVariant = Color(0xFFADB6CD),
        error = Color(0xFFF52A65),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFFE5BACA),
        onErrorContainer = Color(0xFF9A1A40),
        surfaceContainerLowest = Color(0xFFF0F0F3),
        surfaceContainerLow = Color(0xFFDBDDE4),
        surfaceContainer = Color(0xFFD6D9E1), // Navigation bar background
        surfaceContainerHigh = Color(0xFFD0D4DF),
        surfaceContainerHighest = Color(0xFFCBCFDC),
    )
}
// KMK <--
