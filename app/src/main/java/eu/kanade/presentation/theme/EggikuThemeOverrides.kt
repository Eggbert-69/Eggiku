package eu.kanade.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Which part of the custom theme a color replaces */
enum class EggikuThemeColorRole { BACKGROUND, ACCENT, TEXT }

fun UiPreferences.themeOverride(role: EggikuThemeColorRole) = when (role) {
    EggikuThemeColorRole.BACKGROUND -> customThemeBackground()
    EggikuThemeColorRole.ACCENT -> customThemeAccent()
    EggikuThemeColorRole.TEXT -> customThemeText()
}

/**
 * Applies the user's own background, accent and text colors on top of the generated custom theme.
 * A stored value of 0 means "automatic".
 */
fun ColorScheme.withEggikuOverrides(prefs: UiPreferences = Injekt.get()): ColorScheme {
    val background = prefs.customThemeBackground().get().takeIf { it != 0 }?.let(::Color)
    val accent = prefs.customThemeAccent().get().takeIf { it != 0 }?.let(::Color)
    val text = prefs.customThemeText().get().takeIf { it != 0 }?.let(::Color)
    if (background == null && accent == null && text == null) return this

    var scheme = this
    val bg = background ?: scheme.background
    val fg = text ?: if (background != null) readableOn(bg) else scheme.onBackground

    if (background != null || text != null) {
        scheme = scheme.copy(
            background = bg,
            surface = bg,
            onBackground = fg,
            onSurface = fg,
            onSurfaceVariant = lerp(fg, bg, 0.2f),
            surfaceVariant = lerp(bg, fg, 0.08f),
            surfaceContainerLowest = lerp(bg, fg, 0.01f),
            surfaceContainerLow = lerp(bg, fg, 0.04f),
            surfaceContainer = lerp(bg, fg, 0.07f),
            surfaceContainerHigh = lerp(bg, fg, 0.10f),
            surfaceContainerHighest = lerp(bg, fg, 0.14f),
            inverseSurface = fg,
            inverseOnSurface = bg,
            outline = lerp(fg, bg, 0.5f),
            outlineVariant = lerp(fg, bg, 0.75f),
        )
    }
    if (accent != null) {
        val onAccent = readableOn(accent)
        scheme = scheme.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(bg, accent, 0.35f),
            onPrimaryContainer = readableOn(lerp(bg, accent, 0.35f)),
            secondaryContainer = lerp(bg, accent, 0.25f),
            onSecondaryContainer = readableOn(lerp(bg, accent, 0.25f)),
            surfaceTint = accent,
            inversePrimary = lerp(accent, bg, 0.4f),
        )
    }
    return scheme
}

private fun readableOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF111111) else Color(0xFFF5F5F5)

/** Shareable description of a custom theme */
@Serializable
data class EggikuThemeFile(
    val version: Int = 1,
    val seed: Int,
    val style: String,
    val background: Int = 0,
    val accent: Int = 0,
    val text: Int = 0,
)

object EggikuThemeCodec {
    private const val PREFIX = "eggiku-theme:"
    private val json = Json { ignoreUnknownKeys = true }

    fun export(prefs: UiPreferences): String {
        val file = EggikuThemeFile(
            seed = prefs.colorTheme().get(),
            style = prefs.customThemeStyle().get().name,
            background = prefs.customThemeBackground().get(),
            accent = prefs.customThemeAccent().get(),
            text = prefs.customThemeText().get(),
        )
        return PREFIX + json.encodeToString(EggikuThemeFile.serializer(), file)
    }

    /** @return true when the text was a valid theme and has been applied */
    fun import(prefs: UiPreferences, text: String): Boolean {
        val body = text.trim().removePrefix(PREFIX)
        val file = runCatching { json.decodeFromString(EggikuThemeFile.serializer(), body) }.getOrNull()
            ?: return false
        prefs.colorTheme().set(file.seed)
        PaletteStyle.entries.firstOrNull { it.name == file.style }?.let { prefs.customThemeStyle().set(it) }
        prefs.customThemeBackground().set(file.background)
        prefs.customThemeAccent().set(file.accent)
        prefs.customThemeText().set(file.text)
        prefs.appTheme().set(AppTheme.CUSTOM)
        return true
    }
}
// KMK <--
