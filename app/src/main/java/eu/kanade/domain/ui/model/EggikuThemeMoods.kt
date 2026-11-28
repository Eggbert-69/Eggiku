package eu.kanade.domain.ui.model

// KMK -->
/** Eggiku: the filters on the theme picker */
enum class EggikuThemeMood {
    ALL,
    NEW,
    COZY,
    BOLD,
    CALM,
    EGGIKU,
}

/** Themes added in the latest wave, shown under "New" */
val NewAppThemes = setOf(
    AppTheme.ESPRESSO,
    AppTheme.INK_PAPER,
    AppTheme.MATCHA_LATTE,
    AppTheme.RAMEN_NIGHT,
    AppTheme.SAKURA_DRIFT,
    AppTheme.AURORA,
    AppTheme.CAMPFIRE,
    AppTheme.BUBBLE_TEA,
    AppTheme.POCKET_PIXEL,
    AppTheme.FROSTBITE,
    AppTheme.THUNDERSTORM,
)

private val cozy = setOf(
    AppTheme.MOCHA, AppTheme.ESPRESSO, AppTheme.MATCHA_LATTE, AppTheme.RAMEN_NIGHT, AppTheme.CAMPFIRE,
    AppTheme.BUBBLE_TEA, AppTheme.COTTONCANDY, AppTheme.SAKURA_DRIFT, AppTheme.GRUVBOX, AppTheme.EVERFOREST,
    AppTheme.YOTSUBA, AppTheme.STRAWBERRY_DAIQUIRI, AppTheme.GOLDEN_HOUR, AppTheme.CATPPUCCIN, AppTheme.ROSE_PINE,
)
private val bold = setOf(
    AppTheme.DOOM, AppTheme.MATRIX, AppTheme.CLOUDFLARE, AppTheme.DRACULA, AppTheme.THUNDERSTORM,
    AppTheme.POCKET_PIXEL, AppTheme.HOT_SAUCE, AppTheme.MIDNIGHT_ARCADE, AppTheme.GLIB_GLORB, AppTheme.AURORA,
    AppTheme.TAKO, AppTheme.INK_PAPER, AppTheme.YINYANG,
)
private val calm = setOf(
    AppTheme.DEFAULT, AppTheme.MONET, AppTheme.NORD, AppTheme.TIDAL_WAVE, AppTheme.TEALTURQUOISE,
    AppTheme.LAVENDER, AppTheme.MIDNIGHT_DUSK, AppTheme.MONOCHROME, AppTheme.SAPPHIRE, AppTheme.TOKYO_NIGHT,
    AppTheme.KANAGAWA, AppTheme.ONE_DARK, AppTheme.SOLARIZED, AppTheme.FROSTBITE, AppTheme.GREEN_APPLE,
    AppTheme.CUSTOM,
)
private val eggiku = setOf(
    AppTheme.SUNNY_SIDE_UP,
    AppTheme.DINO_EGG,
    AppTheme.EASTER_BASKET,
    AppTheme.GOLDEN_HOUR,
    AppTheme.GLIB_GLORB,
    AppTheme.HOT_SAUCE,
    AppTheme.MIDNIGHT_ARCADE,
    AppTheme.ESPRESSO,
)

fun AppTheme.inMood(mood: EggikuThemeMood): Boolean = when (mood) {
    EggikuThemeMood.ALL -> true
    EggikuThemeMood.NEW -> this in NewAppThemes
    EggikuThemeMood.COZY -> this in cozy
    EggikuThemeMood.BOLD -> this in bold
    EggikuThemeMood.CALM -> this in calm
    EggikuThemeMood.EGGIKU -> this in eggiku
}
// KMK <--
