package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Everforest theme (Eggiku)
 * Palette: https://github.com/sainnhe/everforest
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object EverforestColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA7C080),
        onPrimary = Color(0xFF2D353B),
        primaryContainer = Color(0xFF546151),
        onPrimaryContainer = Color(0xFFCFDDBA),
        inversePrimary = Color(0xFF64734D),
        secondary = Color(0xFF83C092), // Unread badge
        onSecondary = Color(0xFF2D353B), // Unread badge text
        secondaryContainer = Color(0xFF496157), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFBCDDC4), // Navigation bar selector icon
        tertiary = Color(0xFFDBBC7F), // Downloaded badge
        onTertiary = Color(0xFF2D353B), // Downloaded badge text
        tertiaryContainer = Color(0xFF656051),
        onTertiaryContainer = Color(0xFFEAD9B6),
        background = Color(0xFF2D353B),
        onBackground = Color(0xFFD3C6AA),
        surface = Color(0xFF2D353B),
        onSurface = Color(0xFFD3C6AA),
        surfaceVariant = Color(0xFF393F43), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFB5AC96),
        surfaceTint = Color(0xFFA7C080),
        inverseSurface = Color(0xFFD3C6AA),
        inverseOnSurface = Color(0xFF2D353B),
        outline = Color(0xFF807E72),
        outlineVariant = Color(0xFF5B5E5A),
        error = Color(0xFFE67E80),
        onError = Color(0xFF2D353B),
        errorContainer = Color(0xFF684C51),
        onErrorContainer = Color(0xFFF2B9BA),
        surfaceContainerLowest = Color(0xFF252B30),
        surfaceContainerLow = Color(0xFF343B3F),
        surfaceContainer = Color(0xFF393F43), // Navigation bar background
        surfaceContainerHigh = Color(0xFF3E4446),
        surfaceContainerHighest = Color(0xFF44494B),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF829401),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFE2E0B1),
        onPrimaryContainer = Color(0xFF5A6601),
        inversePrimary = Color(0xFFB4BF67),
        secondary = Color(0xFF33A077), // Unread badge
        onSecondary = Color(0xFF000000), // Unread badge text
        secondaryContainer = Color(0xFFD1E3CB), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF236F52), // Navigation bar selector icon
        tertiary = Color(0xFFB28000), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFFEDDCB1),
        onTertiaryContainer = Color(0xFF7B5800),
        background = Color(0xFFFDF6E3),
        onBackground = Color(0xFF4A555B),
        surface = Color(0xFFFDF6E3),
        onSurface = Color(0xFF4A555B),
        surfaceVariant = Color(0xFFF2ECDB), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF61696A),
        surfaceTint = Color(0xFF829401),
        inverseSurface = Color(0xFF4A555B),
        inverseOnSurface = Color(0xFFFDF6E3),
        outline = Color(0xFF898B86),
        outlineVariant = Color(0xFFCBC9BD),
        error = Color(0xFFF85552),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFFFCD3C3),
        onErrorContainer = Color(0xFFA43836),
        surfaceContainerLowest = Color(0xFFFEFAF1),
        surfaceContainerLow = Color(0xFFF8F1DF),
        surfaceContainer = Color(0xFFF2ECDB), // Navigation bar background
        surfaceContainerHigh = Color(0xFFEDE7D7),
        surfaceContainerHighest = Color(0xFFE7E3D3),
    )
}
// KMK <--
