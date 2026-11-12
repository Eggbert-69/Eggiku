package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// KMK -->
/**
 * Colors for Kanagawa theme (Eggiku)
 * Palette: https://github.com/rebelot/kanagawa.nvim
 * Generated from the theme's official palette; on-colors are picked for readable contrast.
 */
internal object KanagawaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF7E9CD8),
        onPrimary = Color(0xFF1F1F28),
        primaryContainer = Color(0xFF3D4760),
        onPrimaryContainer = Color(0xFFA2B8E3),
        inversePrimary = Color(0xFF4C5E82),
        secondary = Color(0xFF957FB8), // Unread badge
        onSecondary = Color(0xFF1F1F28), // Unread badge text
        secondaryContainer = Color(0xFF453E56), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFB6A7CE), // Navigation bar selector icon
        tertiary = Color(0xFF98BB6C), // Downloaded badge
        onTertiary = Color(0xFF1F1F28), // Downloaded badge text
        tertiaryContainer = Color(0xFF46513E),
        onTertiaryContainer = Color(0xFFB2CC91),
        background = Color(0xFF1F1F28),
        onBackground = Color(0xFFDCD7BA),
        surface = Color(0xFF1F1F28),
        onSurface = Color(0xFFDCD7BA),
        surfaceVariant = Color(0xFF2C2C32), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFBAB6A0),
        surfaceTint = Color(0xFF7E9CD8),
        inverseSurface = Color(0xFFDCD7BA),
        inverseOnSurface = Color(0xFF1F1F28),
        outline = Color(0xFF7E7B71),
        outlineVariant = Color(0xFF545351),
        error = Color(0xFFE46876),
        onError = Color(0xFF1F1F28),
        errorContainer = Color(0xFF5E3641),
        onErrorContainer = Color(0xFFEC97A0),
        surfaceContainerLowest = Color(0xFF191921),
        surfaceContainerLow = Color(0xFF27262E),
        surfaceContainer = Color(0xFF2C2C32), // Navigation bar background
        surfaceContainerHigh = Color(0xFF323137),
        surfaceContainerHighest = Color(0xFF39393C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF4D699B),
        onPrimary = Color(0xFFF2ECBC),
        primaryContainer = Color(0xFFCECFB5),
        onPrimaryContainer = Color(0xFF3A4F74),
        inversePrimary = Color(0xFF94A5C3),
        secondary = Color(0xFF624C83), // Unread badge
        onSecondary = Color(0xFFF2ECBC), // Unread badge text
        secondaryContainer = Color(0xFFD2C9AF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF4A3962), // Navigation bar selector icon
        tertiary = Color(0xFF6F894E), // Downloaded badge
        onTertiary = Color(0xFF000000), // Downloaded badge text
        tertiaryContainer = Color(0xFFD5D6A4),
        onTertiaryContainer = Color(0xFF4D5F36),
        background = Color(0xFFF2ECBC),
        onBackground = Color(0xFF4A4A58),
        surface = Color(0xFFF2ECBC),
        onSurface = Color(0xFF4A4A58),
        surfaceVariant = Color(0xFFE8E2B6), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF646366),
        surfaceTint = Color(0xFF4D699B),
        inverseSurface = Color(0xFF4A4A58),
        inverseOnSurface = Color(0xFFF2ECBC),
        outline = Color(0xFF858274),
        outlineVariant = Color(0xFFC3BFA0),
        error = Color(0xFFC84053),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFE9C6A5),
        onErrorContainer = Color(0xFF96303E),
        surfaceContainerLowest = Color(0xFFF8F6DE),
        surfaceContainerLow = Color(0xFFEDE7B9),
        surfaceContainer = Color(0xFFE8E2B6), // Navigation bar background
        surfaceContainerHigh = Color(0xFFE3DDB3),
        surfaceContainerHighest = Color(0xFFDED9B0),
    )
}
// KMK <--
