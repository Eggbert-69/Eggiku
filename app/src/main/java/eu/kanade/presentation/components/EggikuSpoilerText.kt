package eu.kanade.presentation.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Eggiku spoiler shield: chapter titles past where you've read stay blurred until you get there */
object EggikuSpoilerShield {
    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    fun pref() = preferenceStore.getBoolean("eggiku_spoiler_shield", true)

    private val withSeparator = Regex("""^(.*\d+(?:\.\d+)?)(\s*[:\-–—|]\s*)(\S.*)$""")
    private val withSpace = Regex("""^(.*?\d+(?:\.\d+)?)(\s+)(\S.*)$""")
    private val hasDigit = Regex("""\d""")

    /**
     * Splits "Chapter 45: The betrayal" into the part that's safe to show ("Chapter 45: ") and the
     * part to hide ("The betrayal"). A title that's only "Chapter 45" has nothing to hide.
     */
    fun split(title: String): Pair<String, String> {
        val trimmed = title.trim()
        val match = withSeparator.find(trimmed) ?: withSpace.find(trimmed)
        if (match != null) {
            val (lead, separator, rest) = match.destructured
            return (lead + separator) to rest
        }
        return if (hasDigit.containsMatchIn(trimmed)) trimmed to "" else "" to trimmed
    }
}

/**
 * A chapter title that hides the spoiler part when [shielded]. Tap the hidden part to show it.
 * Blurs on Android 12 and up; older versions show dots instead.
 */
@Composable
fun EggikuSpoilerText(
    text: String,
    shielded: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    var revealed by rememberSaveable(text) { mutableStateOf(false) }
    val (safe, hidden) = remember(text) { EggikuSpoilerShield.split(text) }
    if (!shielded || revealed || hidden.isBlank()) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onTextLayout,
        )
        return
    }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Row(modifier = modifier) {
        if (safe.isNotEmpty()) {
            Text(text = safe, style = style, color = color, maxLines = 1, onTextLayout = onTextLayout)
        }
        Text(
            text = if (canBlur) hidden else "•".repeat(hidden.length.coerceIn(3, 12)),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            onTextLayout = { if (safe.isEmpty()) onTextLayout(it) },
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    revealed = true
                }
                .then(if (canBlur) Modifier.blur(6.dp) else Modifier),
        )
    }
}
// KMK <--
