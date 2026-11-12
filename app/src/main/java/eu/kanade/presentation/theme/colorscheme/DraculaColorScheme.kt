package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Dracula theme (Eggiku)
 * Palette: https://draculatheme.com/contribute
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object DraculaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFBD93F9),
        onPrimary = Color(0xFF282A36),
        primaryContainer = Color(0xFF584C74),
        onPrimaryContainer = Color(0xFFD5BBFB),
        inversePrimary = Color(0xFF715895),
        secondary = Color(0xFFFF79C6), // Unread badge
        onSecondary = Color(0xFF282A36), // Unread badge text
        secondaryContainer = Color(0xFF6D4364), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFFABDB), // Navigation bar selector icon
        tertiary = Color(0xFF50FA7B), // Downloaded badge
        onTertiary = Color(0xFF282A36), // Downloaded badge text
        tertiaryContainer = Color(0xFF356D4C),
        onTertiaryContainer = Color(0xFF7CFB9C),
        background = Color(0xFF282A36),
        onBackground = Color(0xFFF8F8F2),
        surface = Color(0xFF282A36),
        onSurface = Color(0xFFF8F8F2),
        surfaceVariant = Color(0xFF373843), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFD3D3D0),
        surfaceTint = Color(0xFFBD93F9),
        inverseSurface = Color(0xFFF8F8F2),
        inverseOnSurface = Color(0xFF282A36),
        outline = Color(0xFF909194),
        outlineVariant = Color(0xFF62646B),
        error = Color(0xFFFF5555),
        onError = Color(0xFF282A36),
        errorContainer = Color(0xFF6D3840),
        onErrorContainer = Color(0xFFFF9E9E),
        surfaceContainerLowest = Color(0xFF21222C),
        surfaceContainerLow = Color(0xFF30323E),
        surfaceContainer = Color(0xFF373843), // Navigation bar background
        surfaceContainerHigh = Color(0xFF3D3F49),
        surfaceContainerHighest = Color(0xFF454750),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF644AC9),
        onPrimary = Color(0xFFFFFBEB),
        primaryContainer = Color(0xFFDDD4E4),
        onPrimaryContainer = Color(0xFF4B3897),
        inversePrimary = Color(0xFFA292DF),
        secondary = Color(0xFFA3144D), // Unread badge
        onSecondary = Color(0xFFFFFBEB), // Unread badge text
        secondaryContainer = Color(0xFFEBC8C8), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF7A0F3A), // Navigation bar selector icon
        tertiary = Color(0xFF14710A), // Downloaded badge
        onTertiary = Color(0xFFFFFBEB), // Downloaded badge text
        tertiaryContainer = Color(0xFFCBDDBA),
        onTertiaryContainer = Color(0xFF0F5508),
        background = Color(0xFFFFFBEB),
        onBackground = Color(0xFF1F1F1F),
        surface = Color(0xFFFFFBEB),
        onSurface = Color(0xFF1F1F1F),
        surfaceVariant = Color(0xFFF2EEDF), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF474744),
        surfaceTint = Color(0xFF644AC9),
        inverseSurface = Color(0xFF1F1F1F),
        inverseOnSurface = Color(0xFFFFFBEB),
        outline = Color(0xFF8F8D85),
        outlineVariant = Color(0xFFC0BDB2),
        error = Color(0xFFCB3A2A),
        onError = Color(0xFFFFFBEB),
        errorContainer = Color(0xFFF4D1C1),
        onErrorContainer = Color(0xFF982C20),
        surfaceContainerLowest = Color(0xFFFFFDF5),
        surfaceContainerLow = Color(0xFFF8F4E5),
        surfaceContainer = Color(0xFFF2EEDF), // Navigation bar background
        surfaceContainerHigh = Color(0xFFEBE7D9),
        surfaceContainerHighest = Color(0xFFE4E1D3),
    )
}
// KMK <--
