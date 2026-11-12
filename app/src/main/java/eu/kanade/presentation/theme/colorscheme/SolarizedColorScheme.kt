package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Solarized theme (Eggiku)
 * Palette: https://ethanschoonover.com/solarized/
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object SolarizedColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF268BD2),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF0C4A68),
        onPrimaryContainer = Color(0xFF7DB9E4),
        inversePrimary = Color(0xFF17537E),
        secondary = Color(0xFF2AA198), // Unread badge
        onSecondary = Color(0xFF002B36), // Unread badge text
        secondaryContainer = Color(0xFF0D5155), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF7FC7C1), // Navigation bar selector icon
        tertiary = Color(0xFFB58900), // Downloaded badge
        onTertiary = Color(0xFF002B36), // Downloaded badge text
        tertiaryContainer = Color(0xFF3A4925),
        onTertiaryContainer = Color(0xFFCEB157),
        background = Color(0xFF002B36),
        onBackground = Color(0xFFA9B4B4),
        surface = Color(0xFF002B36),
        onSurface = Color(0xFFA9B4B4),
        surfaceVariant = Color(0xFF0C353F), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF8A9B9D),
        surfaceTint = Color(0xFF268BD2),
        inverseSurface = Color(0xFFA9B4B4),
        inverseOnSurface = Color(0xFF002B36),
        outline = Color(0xFF5B757A),
        outlineVariant = Color(0xFF2F5159),
        error = Color(0xFFDC322F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFF462D34),
        onErrorContainer = Color(0xFFE97E7C),
        surfaceContainerLowest = Color(0xFF00232C),
        surfaceContainerLow = Color(0xFF07303B),
        surfaceContainer = Color(0xFF0C353F), // Navigation bar background
        surfaceContainerHigh = Color(0xFF113943),
        surfaceContainerHighest = Color(0xFF183E48),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF268BD2),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFCEDEDF),
        onPrimaryContainer = Color(0xFF1B6497),
        inversePrimary = Color(0xFF7DB9E4),
        secondary = Color(0xFF289B92), // Unread badge
        onSecondary = Color(0xFF000000), // Unread badge text
        secondaryContainer = Color(0xFFCEE2D1), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF1C6B65), // Navigation bar selector icon
        tertiary = Color(0xFFAE8400), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFFECDDB1),
        onTertiaryContainer = Color(0xFF785B00),
        background = Color(0xFFFDF6E3),
        onBackground = Color(0xFF435459),
        surface = Color(0xFFFDF6E3),
        onSurface = Color(0xFF435459),
        surfaceVariant = Color(0xFFF2ECDB), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF606C6D),
        surfaceTint = Color(0xFF268BD2),
        inverseSurface = Color(0xFF435459),
        inverseOnSurface = Color(0xFFFDF6E3),
        outline = Color(0xFF868A85),
        outlineVariant = Color(0xFFC9C9BC),
        error = Color(0xFFDC322F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF6CBBB),
        onErrorContainer = Color(0xFFA52623),
        surfaceContainerLowest = Color(0xFFFEFAF1),
        surfaceContainerLow = Color(0xFFF7F1DF),
        surfaceContainer = Color(0xFFF2ECDB), // Navigation bar background
        surfaceContainerHigh = Color(0xFFECE7D7),
        surfaceContainerHighest = Color(0xFFE7E3D2),
    )
}
// KMK <--
