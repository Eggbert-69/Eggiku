package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),

    // Kuukiyomi themes
    CUSTOM(KMR.strings.theme_custom),

    // Aniyomi themes
    COTTONCANDY(KMR.strings.theme_cottoncandy),
    MOCHA(KMR.strings.theme_mocha),

    CATPPUCCIN(MR.strings.theme_catppuccin),
    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),
    NORD(MR.strings.theme_nord),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),
    MONOCHROME(MR.strings.theme_monochrome),

    // Aniyomi themes
    CLOUDFLARE(KMR.strings.theme_cloudflare),
    DOOM(KMR.strings.theme_doom),
    MATRIX(KMR.strings.theme_matrix),
    SAPPHIRE(KMR.strings.theme_sapphire),

    // KMK -->
    // Eggiku themes
    DRACULA(KMR.strings.theme_dracula),
    GRUVBOX(KMR.strings.theme_gruvbox),
    TOKYO_NIGHT(KMR.strings.theme_tokyonight),
    ROSE_PINE(KMR.strings.theme_rosepine),
    EVERFOREST(KMR.strings.theme_everforest),
    KANAGAWA(KMR.strings.theme_kanagawa),
    ONE_DARK(KMR.strings.theme_onedark),
    SOLARIZED(KMR.strings.theme_solarized),

    /** Hidden until unlocked: type "wyatt" in the settings search and launch the rocket */
    GLIB_GLORB(KMR.strings.theme_glibglorb),

    /** Hidden until unlocked: tap the version in About 7 times */
    SUNNY_SIDE_UP(KMR.strings.theme_sunnysideup),

    /** Hidden until unlocked: search "insert coin" in settings */
    MIDNIGHT_ARCADE(KMR.strings.theme_midnightarcade),

    /** Hidden until unlocked: read 7 days in a row */
    HOT_SAUCE(KMR.strings.theme_hotsauce),

    /** Hidden until unlocked: earn 50 achievements */
    GOLDEN_HOUR(KMR.strings.theme_goldenhour),

    /** Hidden until unlocked: search "rawr" in settings and hatch the egg */
    DINO_EGG(KMR.strings.theme_dinoegg),
    // KMK <--

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),

    // SY -->
    PURE_RED(null),
    // SY <--
}

// KMK -->
/** Eggiku themes that stay out of the theme list until you find them */
val HiddenAppThemes = setOf(
    AppTheme.GLIB_GLORB,
    AppTheme.SUNNY_SIDE_UP,
    AppTheme.MIDNIGHT_ARCADE,
    AppTheme.HOT_SAUCE,
    AppTheme.GOLDEN_HOUR,
    AppTheme.DINO_EGG,
)
// KMK <--
