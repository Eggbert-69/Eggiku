package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for One Dark theme (Eggiku)
 * Palette: https://github.com/atom/atom/tree/master/packages/one-dark-syntax
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object OneDarkColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF61AFEF),
        onPrimary = Color(0xFF282C34),
        primaryContainer = Color(0xFF3A5670),
        onPrimaryContainer = Color(0xFF9BCDF5),
        inversePrimary = Color(0xFF3A698F),
        secondary = Color(0xFFC678DD), // Unread badge
        onSecondary = Color(0xFF282C34), // Unread badge text
        secondaryContainer = Color(0xFF5B446A), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFDDAEEB), // Navigation bar selector icon
        tertiary = Color(0xFF98C379), // Downloaded badge
        onTertiary = Color(0xFF282C34), // Downloaded badge text
        tertiaryContainer = Color(0xFF4C5C4A),
        onTertiaryContainer = Color(0xFFBBD7A7),
        background = Color(0xFF282C34),
        onBackground = Color(0xFFB2B8C4),
        surface = Color(0xFF282C34),
        onSurface = Color(0xFFB2B8C4),
        surfaceVariant = Color(0xFF32363E), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF999FAA),
        surfaceTint = Color(0xFF61AFEF),
        inverseSurface = Color(0xFFB2B8C4),
        inverseOnSurface = Color(0xFF282C34),
        outline = Color(0xFF737881),
        outlineVariant = Color(0xFF4F535C),
        error = Color(0xFFE06C75),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF634049),
        onErrorContainer = Color(0xFFECA7AC),
        surfaceContainerLowest = Color(0xFF21242B),
        surfaceContainerLow = Color(0xFF2E323A),
        surfaceContainer = Color(0xFF32363E), // Navigation bar background
        surfaceContainerHigh = Color(0xFF363A42),
        surfaceContainerHighest = Color(0xFF3B4048),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF4078F2),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFD1DDF8),
        onPrimaryContainer = Color(0xFF305AB6),
        inversePrimary = Color(0xFF8CAEF7),
        secondary = Color(0xFFA626A4), // Unread badge
        onSecondary = Color(0xFFFAFAFA), // Unread badge text
        secondaryContainer = Color(0xFFE8CBE7), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF7C1C7B), // Navigation bar selector icon
        tertiary = Color(0xFF50A14F), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFFD5E6D4),
        onTertiaryContainer = Color(0xFF376F37),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF383A42),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF383A42),
        surfaceVariant = Color(0xFFEEEEEF), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF5B5D63),
        surfaceTint = Color(0xFF4078F2),
        inverseSurface = Color(0xFF383A42),
        inverseOnSurface = Color(0xFFFAFAFA),
        outline = Color(0xFF8D8E91),
        outlineVariant = Color(0xFFC4C4C6),
        error = Color(0xFFE45649),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFFF5D6D3),
        onErrorContainer = Color(0xFFA43E35),
        surfaceContainerLowest = Color(0xFFFCFCFC),
        surfaceContainerLow = Color(0xFFF4F4F4),
        surfaceContainer = Color(0xFFEEEEEF), // Navigation bar background
        surfaceContainerHigh = Color(0xFFE9E9E9),
        surfaceContainerHighest = Color(0xFFE3E3E4),
    )
}
// KMK <--
