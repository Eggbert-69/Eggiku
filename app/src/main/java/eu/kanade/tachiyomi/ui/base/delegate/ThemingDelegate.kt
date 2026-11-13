package eu.kanade.tachiyomi.ui.base.delegate

import android.app.Activity
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.tachiyomi.R
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ThemingDelegate {
    fun applyAppTheme(activity: Activity)

    companion object {
        fun getThemeResIds(appTheme: AppTheme, isAmoled: Boolean): List<Int> {
            return buildList(2) {
                add(themeResources.getOrDefault(appTheme, R.style.Theme_Tachiyomi))
                // KMK -->
                if (isAmoled || appTheme == AppTheme.GLIB_GLORB) add(R.style.ThemeOverlay_Tachiyomi_Amoled)
                // KMK <--
            }
        }
    }
}

class ThemingDelegateImpl : ThemingDelegate {
    override fun applyAppTheme(activity: Activity) {
        val uiPreferences = Injekt.get<UiPreferences>()
        ThemingDelegate.getThemeResIds(uiPreferences.appTheme().get(), uiPreferences.themeDarkAmoled().get())
            .forEach(activity::setTheme)
    }
}

private val themeResources: Map<AppTheme, Int> = mapOf(
    AppTheme.MONET to R.style.Theme_Tachiyomi_Monet,
    AppTheme.CUSTOM to R.style.Theme_Tachiyomi_Custom,
    AppTheme.CATPPUCCIN to R.style.Theme_Tachiyomi_Catppuccin,
    AppTheme.GREEN_APPLE to R.style.Theme_Tachiyomi_GreenApple,
    AppTheme.LAVENDER to R.style.Theme_Tachiyomi_Lavender,
    AppTheme.MIDNIGHT_DUSK to R.style.Theme_Tachiyomi_MidnightDusk,
    AppTheme.MONOCHROME to R.style.Theme_Tachiyomi_Monochrome,
    AppTheme.NORD to R.style.Theme_Tachiyomi_Nord,
    AppTheme.STRAWBERRY_DAIQUIRI to R.style.Theme_Tachiyomi_StrawberryDaiquiri,
    AppTheme.TAKO to R.style.Theme_Tachiyomi_Tako,
    AppTheme.TEALTURQUOISE to R.style.Theme_Tachiyomi_TealTurquoise,
    AppTheme.YINYANG to R.style.Theme_Tachiyomi_YinYang,
    AppTheme.YOTSUBA to R.style.Theme_Tachiyomi_Yotsuba,
    AppTheme.TIDAL_WAVE to R.style.Theme_Tachiyomi_TidalWave,
    AppTheme.COTTONCANDY to R.style.Theme_Tachiyomi_CottonCandy,
    AppTheme.MOCHA to R.style.Theme_Tachiyomi_Mocha,
    AppTheme.CLOUDFLARE to R.style.Theme_Tachiyomi_Cloudflare,
    AppTheme.SAPPHIRE to R.style.Theme_Tachiyomi_Sapphire,
    AppTheme.DOOM to R.style.Theme_Tachiyomi_Doom,
    AppTheme.MATRIX to R.style.Theme_Tachiyomi_Matrix,
    // KMK -->
    AppTheme.DRACULA to R.style.Theme_Tachiyomi_Dracula,
    AppTheme.GRUVBOX to R.style.Theme_Tachiyomi_Gruvbox,
    AppTheme.TOKYO_NIGHT to R.style.Theme_Tachiyomi_TokyoNight,
    AppTheme.ROSE_PINE to R.style.Theme_Tachiyomi_RosePine,
    AppTheme.EVERFOREST to R.style.Theme_Tachiyomi_Everforest,
    AppTheme.KANAGAWA to R.style.Theme_Tachiyomi_Kanagawa,
    AppTheme.ONE_DARK to R.style.Theme_Tachiyomi_OneDark,
    AppTheme.SOLARIZED to R.style.Theme_Tachiyomi_Solarized,
    AppTheme.GLIB_GLORB to R.style.Theme_Tachiyomi_Matrix,
    AppTheme.SUNNY_SIDE_UP to R.style.Theme_Tachiyomi_Yotsuba,
    AppTheme.MIDNIGHT_ARCADE to R.style.Theme_Tachiyomi_StrawberryDaiquiri,
    AppTheme.HOT_SAUCE to R.style.Theme_Tachiyomi_StrawberryDaiquiri,
    AppTheme.GOLDEN_HOUR to R.style.Theme_Tachiyomi_Yotsuba,
    AppTheme.DINO_EGG to R.style.Theme_Tachiyomi_GreenApple,
    // KMK <--
)
