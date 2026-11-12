package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Rosé Pine theme (Eggiku)
 * Palette: https://rosepinetheme.com/palette
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object RosePineColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFEBBCBA),
        onPrimary = Color(0xFF191724),
        primaryContainer = Color(0xFF5C4C54),
        onPrimaryContainer = Color(0xFFF0CDCB),
        inversePrimary = Color(0xFF8D7170),
        secondary = Color(0xFFC4A7E7), // Unread badge
        onSecondary = Color(0xFF191724), // Unread badge text
        secondaryContainer = Color(0xFF504562), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFD3BDED), // Navigation bar selector icon
        tertiary = Color(0xFF9CCFD8), // Downloaded badge
        onTertiary = Color(0xFF191724), // Downloaded badge text
        tertiaryContainer = Color(0xFF43525E),
        onTertiaryContainer = Color(0xFFB5DBE2),
        background = Color(0xFF191724),
        onBackground = Color(0xFFE0DEF4),
        surface = Color(0xFF191724),
        onSurface = Color(0xFFE0DEF4),
        surfaceVariant = Color(0xFF272533), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFBCBACF),
        surfaceTint = Color(0xFFEBBCBA),
        inverseSurface = Color(0xFFE0DEF4),
        inverseOnSurface = Color(0xFF191724),
        outline = Color(0xFF7C7A8C),
        outlineVariant = Color(0xFF514F5E),
        error = Color(0xFFEB6F92),
        onError = Color(0xFF191724),
        errorContainer = Color(0xFF5C3347),
        onErrorContainer = Color(0xFFF093AD),
        surfaceContainerLowest = Color(0xFF14131E),
        surfaceContainerLow = Color(0xFF211F2C),
        surfaceContainer = Color(0xFF272533), // Navigation bar background
        surfaceContainerHigh = Color(0xFF2D2B39),
        surfaceContainerHighest = Color(0xFF353341),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF286983),
        onPrimary = Color(0xFFFAF4ED),
        primaryContainer = Color(0xFFCCD5D6),
        onPrimaryContainer = Color(0xFF1E4F62),
        inversePrimary = Color(0xFF7EA5B5),
        secondary = Color(0xFF907AA9), // Unread badge
        onSecondary = Color(0xFF000000), // Unread badge text
        secondaryContainer = Color(0xFFE3D9DE), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF68587A), // Navigation bar selector icon
        tertiary = Color(0xFFC67874), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFFEFD9D2),
        onTertiaryContainer = Color(0xFF885350),
        background = Color(0xFFFAF4ED),
        onBackground = Color(0xFF544F74),
        surface = Color(0xFFFAF4ED),
        onSurface = Color(0xFF544F74),
        surfaceVariant = Color(0xFFF0EAE6), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF68647F),
        surfaceTint = Color(0xFF286983),
        inverseSurface = Color(0xFF544F74),
        inverseOnSurface = Color(0xFFFAF4ED),
        outline = Color(0xFF8C8894),
        outlineVariant = Color(0xFFCBC6CB),
        error = Color(0xFFB4637A),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFFEBD4D4),
        onErrorContainer = Color(0xFF874A5C),
        surfaceContainerLowest = Color(0xFFFCFAF6),
        surfaceContainerLow = Color(0xFFF5EFE9),
        surfaceContainer = Color(0xFFF0EAE6), // Navigation bar background
        surfaceContainerHigh = Color(0xFFEBE5E2),
        surfaceContainerHighest = Color(0xFFE6E0DE),
    )
}
// KMK <--
