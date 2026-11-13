package eu.kanade.tachiyomi.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import tachiyomi.i18n.kmk.KMR

// KMK -->
/**
 * Eggiku app icons. Each icon is an alias of MainActivity in the manifest; switching enables one
 * alias and disables the others. Android home screens can't animate icons, so these are still.
 */
object EggikuAppIcons {

    enum class Icon(val alias: String, val drawable: Int, val title: StringResource) {
        Classic("EggikuIconClassic", R.mipmap.ic_launcher, KMR.strings.icon_classic),
        Cracked("EggikuIconCracked", R.mipmap.eggiku_icon_cracked, KMR.strings.icon_cracked),
        Alien("EggikuIconAlien", R.mipmap.eggiku_icon_alien, KMR.strings.icon_alien),
        Golden("EggikuIconGolden", R.mipmap.eggiku_icon_golden, KMR.strings.icon_golden),
    }

    /** Achievements needed for the golden egg */
    const val GOLDEN_AT = 75

    fun isUnlocked(icon: Icon): Boolean =
        icon != Icon.Golden || EggikuAchievements.earned().size >= GOLDEN_AT

    private fun component(context: Context, icon: Icon) =
        ComponentName(context.packageName, "eu.kanade.tachiyomi.${icon.alias}")

    fun current(context: Context): Icon = Icon.entries.firstOrNull { icon ->
        val state = context.packageManager.getComponentEnabledSetting(component(context, icon))
        when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == Icon.Classic
            else -> false
        }
    } ?: Icon.Classic

    fun set(context: Context, icon: Icon) {
        if (!isUnlocked(icon) || current(context) == icon) return
        val pm = context.packageManager
        // Turn the new one on first so there's never a moment with no icon at all
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        Icon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
// KMK <--
