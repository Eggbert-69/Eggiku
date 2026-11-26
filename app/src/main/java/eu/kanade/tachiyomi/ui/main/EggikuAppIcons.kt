package eu.kanade.tachiyomi.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.Month

// KMK -->
/**
 * Eggiku app icons. Each icon is an alias of MainActivity in the manifest; switching enables one
 * alias and disables the others. Android home screens can't animate icons, so these are still.
 *
 * Seasonal mode picks an icon by the date (frosty egg in winter, pumpkin egg in October, and so
 * on), with a secret UFO egg while the Glib Glorb theme is on. Picking any icon yourself locks it.
 * The switch happens while Eggiku is in the background, so the screen you're on never closes.
 */
object EggikuAppIcons {

    enum class Icon(val alias: String, val drawable: Int, val title: StringResource, val seasonal: Boolean = false) {
        Classic("EggikuIconClassic", R.mipmap.ic_launcher, KMR.strings.icon_classic),
        Cracked("EggikuIconCracked", R.mipmap.eggiku_icon_cracked, KMR.strings.icon_cracked),
        Alien("EggikuIconAlien", R.mipmap.eggiku_icon_alien, KMR.strings.icon_alien),
        Golden("EggikuIconGolden", R.mipmap.eggiku_icon_golden, KMR.strings.icon_golden),
        Frosty("EggikuIconFrosty", R.mipmap.eggiku_icon_frosty, KMR.strings.icon_frosty, seasonal = true),
        Firework("EggikuIconFirework", R.mipmap.eggiku_icon_firework, KMR.strings.icon_firework, seasonal = true),
        Spring("EggikuIconSpring", R.mipmap.eggiku_icon_spring, KMR.strings.icon_spring, seasonal = true),
        Summer("EggikuIconSummer", R.mipmap.eggiku_icon_summer, KMR.strings.icon_summer, seasonal = true),
        Autumn("EggikuIconAutumn", R.mipmap.eggiku_icon_autumn, KMR.strings.icon_autumn, seasonal = true),
        Pumpkin("EggikuIconPumpkin", R.mipmap.eggiku_icon_pumpkin, KMR.strings.icon_pumpkin, seasonal = true),
        Ufo("EggikuIconUfo", R.mipmap.eggiku_icon_ufo, KMR.strings.icon_ufo, seasonal = true),
    }

    /** Achievements needed for the golden egg */
    const val GOLDEN_AT = 75

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** 1 = seasonal, 0 = locked to the picked icon, -1 = not decided yet (first run of this build) */
    private fun seasonalPref() = preferenceStore.getInt("eggiku_icon_seasonal", -1)

    /** The seasonal icon that's been introduced with its little pop, so it only happens once */
    fun introducedPref(): Preference<String> =
        preferenceStore.getString(Preference.appStateKey("eggiku_icon_introduced"), "")

    fun isUnlocked(icon: Icon): Boolean = when (icon) {
        Icon.Golden -> EggikuAchievements.earned().size >= GOLDEN_AT
        // The UFO only lands while the Glib Glorb theme is on
        Icon.Ufo -> isGlibGlorb()
        else -> true
    }

    private fun isGlibGlorb(): Boolean = try {
        Injekt.get<UiPreferences>().appTheme().get() == AppTheme.GLIB_GLORB
    } catch (_: Exception) {
        false
    }

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

    /** Seasonal mode is on by default for anyone still on the classic egg */
    fun isSeasonal(context: Context): Boolean {
        val pref = seasonalPref()
        if (pref.get() == -1) pref.set(if (current(context) == Icon.Classic) 1 else 0)
        return pref.get() == 1
    }

    fun setSeasonal(context: Context, on: Boolean) {
        seasonalPref().set(if (on) 1 else 0)
        if (on) set(context, forDate(), lock = false)
    }

    /** The icon for a day, ignoring whether seasonal mode is on */
    fun forDate(date: LocalDate = LocalDate.now(), glibGlorb: Boolean = isGlibGlorb()): Icon {
        if (glibGlorb) return Icon.Ufo
        val day = date.dayOfMonth
        return when (date.month) {
            Month.DECEMBER -> if (day == 31) Icon.Firework else Icon.Frosty
            Month.JANUARY -> if (day <= 2) Icon.Firework else Icon.Frosty
            Month.FEBRUARY -> Icon.Frosty
            Month.MARCH, Month.APRIL, Month.MAY -> Icon.Spring
            Month.JUNE, Month.JULY, Month.AUGUST -> Icon.Summer
            Month.OCTOBER -> Icon.Pumpkin
            Month.SEPTEMBER, Month.NOVEMBER -> Icon.Autumn
        }
    }

    /** Switches to today's seasonal icon if seasonal mode is on. Call while Eggiku is in the background. */
    fun applySeasonal(context: Context) {
        if (!isSeasonal(context)) return
        // Never while Eggiku is on screen; it waits for the next time you leave the app
        val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inForeground) return
        val wanted = forDate()
        if (current(context) != wanted) set(context, wanted, lock = false)
    }

    /** A seasonal icon that's on but hasn't had its welcome yet */
    fun pendingIntroduction(context: Context): Icon? {
        if (!isSeasonal(context)) return null
        val icon = current(context).takeIf { it.seasonal } ?: return null
        val key = "${icon.name}:${LocalDate.now().year}"
        return icon.takeIf { introducedPref().get() != key }
    }

    fun markIntroduced(icon: Icon) {
        introducedPref().set("${icon.name}:${LocalDate.now().year}")
    }

    /** [lock] = picked by hand, which turns seasonal mode off */
    fun set(context: Context, icon: Icon, lock: Boolean = true) {
        if (lock) seasonalPref().set(0)
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
