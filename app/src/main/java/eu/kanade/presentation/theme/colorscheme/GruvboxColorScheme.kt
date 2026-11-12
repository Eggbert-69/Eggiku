package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Gruvbox theme (Eggiku)
 * Palette: https://github.com/morhetz/gruvbox
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object GruvboxColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFABD2F),
        onPrimary = Color(0xFF282828),
        primaryContainer = Color(0xFF6B582A),
        onPrimaryContainer = Color(0xFFFBCE63),
        inversePrimary = Color(0xFF96711C),
        secondary = Color(0xFFFE8019), // Unread badge
        onSecondary = Color(0xFF282828), // Unread badge text
        secondaryContainer = Color(0xFF6C4423), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFEAB67), // Navigation bar selector icon
        tertiary = Color(0xFF8EC07C), // Downloaded badge
        onTertiary = Color(0xFF282828), // Downloaded badge text
        tertiaryContainer = Color(0xFF495943),
        onTertiaryContainer = Color(0xFFB1D4A5),
        background = Color(0xFF282828),
        onBackground = Color(0xFFEBDBB2),
        surface = Color(0xFF282828),
        onSurface = Color(0xFFEBDBB2),
        surfaceVariant = Color(0xFF363532), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFC8BB99),
        surfaceTint = Color(0xFFFABD2F),
        inverseSurface = Color(0xFFEBDBB2),
        inverseOnSurface = Color(0xFF282828),
        outline = Color(0xFF8A826D),
        outlineVariant = Color(0xFF5F5A4F),
        error = Color(0xFFFB4934),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF6C332C),
        onErrorContainer = Color(0xFFFD978B),
        surfaceContainerLowest = Color(0xFF212121),
        surfaceContainerLow = Color(0xFF302F2E),
        surfaceContainer = Color(0xFF363532), // Navigation bar background
        surfaceContainerHigh = Color(0xFF3C3A36),
        surfaceContainerHighest = Color(0xFF43413B),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFB57614),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFECD6A0),
        onPrimaryContainer = Color(0xFF82550E),
        inversePrimary = Color(0xFFD3AD72),
        secondary = Color(0xFFAF3A03), // Unread badge
        onSecondary = Color(0xFFFBF1C7), // Unread badge text
        secondaryContainer = Color(0xFFEAC99C), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF832C02), // Navigation bar selector icon
        tertiary = Color(0xFF427B58), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFD2D7AF),
        onTertiaryContainer = Color(0xFF325C42),
        background = Color(0xFFFBF1C7),
        onBackground = Color(0xFF3C3836),
        surface = Color(0xFFFBF1C7),
        onSurface = Color(0xFF3C3836),
        surfaceVariant = Color(0xFFF0E6BE), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF5E5950),
        surfaceTint = Color(0xFFB57614),
        inverseSurface = Color(0xFF3C3836),
        inverseOnSurface = Color(0xFFFBF1C7),
        outline = Color(0xFF8F8974),
        outlineVariant = Color(0xFFC6BD9E),
        error = Color(0xFF9D0006),
        onError = Color(0xFFFBF1C7),
        errorContainer = Color(0xFFE6BC9D),
        onErrorContainer = Color(0xFF760004),
        surfaceContainerLowest = Color(0xFFFDF8E3),
        surfaceContainerLow = Color(0xFFF5EBC3),
        surfaceContainer = Color(0xFFF0E6BE), // Navigation bar background
        surfaceContainerHigh = Color(0xFFEAE0BA),
        surfaceContainerHighest = Color(0xFFE4DBB6),
    )
}
// KMK <--
