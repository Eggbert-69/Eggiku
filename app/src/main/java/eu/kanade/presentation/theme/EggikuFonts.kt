package eu.kanade.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.kmk.KMR

// KMK -->
/**
 * Eggiku app fonts. All are open fonts (SIL Open Font License) bundled with the app; letters a font
 * doesn't have (e.g. Japanese titles) fall back to the phone's own font.
 */
enum class EggikuFont(val title: StringResource, val regular: Int?, val bold: Int?) {
    SYSTEM(KMR.strings.font_system, null, null),
    NUNITO(KMR.strings.font_nunito, R.font.eggiku_nunito_regular, R.font.eggiku_nunito_bold),
    QUICKSAND(KMR.strings.font_quicksand, R.font.eggiku_quicksand_regular, R.font.eggiku_quicksand_bold),
    COMIC_NEUE(KMR.strings.font_comic_neue, R.font.eggiku_comicneue_regular, R.font.eggiku_comicneue_bold),
    ATKINSON(KMR.strings.font_atkinson, R.font.eggiku_atkinson_regular, R.font.eggiku_atkinson_bold),
    SPACE_GROTESK(KMR.strings.font_space_grotesk, R.font.eggiku_spacegrotesk_regular, R.font.eggiku_spacegrotesk_bold),
    ;

    val family: FontFamily?
        get() {
            if (regular == null || bold == null) return null
            return FontFamily(
                Font(regular, FontWeight.Normal),
                Font(regular, FontWeight.Medium),
                Font(bold, FontWeight.SemiBold),
                Font(bold, FontWeight.Bold),
            )
        }
}

object EggikuFonts {
    fun apply(base: Typography, font: EggikuFont): Typography {
        val family = font.family ?: return base
        fun TextStyle.f() = copy(fontFamily = family)
        return base.copy(
            displayLarge = base.displayLarge.f(),
            displayMedium = base.displayMedium.f(),
            displaySmall = base.displaySmall.f(),
            headlineLarge = base.headlineLarge.f(),
            headlineMedium = base.headlineMedium.f(),
            headlineSmall = base.headlineSmall.f(),
            titleLarge = base.titleLarge.f(),
            titleMedium = base.titleMedium.f(),
            titleSmall = base.titleSmall.f(),
            bodyLarge = base.bodyLarge.f(),
            bodyMedium = base.bodyMedium.f(),
            bodySmall = base.bodySmall.f(),
            labelLarge = base.labelLarge.f(),
            labelMedium = base.labelMedium.f(),
            labelSmall = base.labelSmall.f(),
        )
    }
}
// KMK <--
