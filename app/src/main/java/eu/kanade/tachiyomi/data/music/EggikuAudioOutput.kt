package eu.kanade.tachiyomi.data.music

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

// KMK -->

/**
 * Eggiku: opens the system "audio output" picker (the same sheet as the media player's output
 * button in quick settings) on top of Eggiku, so Bluetooth headphones can be picked without
 * leaving the app. Android doesn't let regular apps connect Bluetooth devices themselves, so this
 * hands off to the system sheet, and falls back to Bluetooth settings on phones that don't have one.
 */
object EggikuAudioOutput {

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    /** Android 12+: SystemUI's output switcher, the same one androidx.mediarouter uses */
    private const val ACTION_OUTPUT_SWITCHER = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"

    /** Android 11: the media output settings panel */
    private const val ACTION_OUTPUT_PANEL_R = "com.android.settings.panel.action.MEDIA_OUTPUT"

    private const val EXTRA_PACKAGE_NAME = "package_name"
    private const val EXTRA_PANEL_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"

    /**
     * Shows the output picker. Returns false only when nothing at all could be opened.
     * Music that's playing in another app (like YouTube Music) is what the picker moves.
     */
    fun show(context: Context): Boolean {
        val target = EggikuMusic.nowPlaying.value?.packageName ?: context.packageName
        return showSystemSwitcher(context, target) ||
            showPanelForAndroidR(context, target) ||
            openBluetoothSettings(context)
    }

    /** Plain Bluetooth settings, for pairing something new */
    fun openBluetoothSettings(context: Context): Boolean {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: ActivityNotFoundException) {
            logcat(LogPriority.WARN, e) { "Eggiku audio output: no Bluetooth settings" }
            false
        }
    }

    private fun showSystemSwitcher(context: Context, targetPackage: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        // Ask SystemUI directly first, so it opens for the music app that's actually playing
        val intent = Intent(ACTION_OUTPUT_SWITCHER)
            .setPackage(SYSTEM_UI_PACKAGE)
            .putExtra(EXTRA_PACKAGE_NAME, targetPackage)
        val receivers = try {
            context.packageManager.queryBroadcastReceivers(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        if (receivers.any { it.isSystemApp() }) {
            try {
                context.sendBroadcast(intent)
                return true
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Eggiku audio output: SystemUI switcher failed" }
            }
        }

        // Android 14+: the public way to open the same sheet (for Eggiku itself)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                if (MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return true
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Eggiku audio output: MediaRouter2 switcher failed" }
            }
        }
        return false
    }

    private fun showPanelForAndroidR(context: Context, targetPackage: String): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R) return false
        val intent = Intent(ACTION_OUTPUT_PANEL_R)
            .putExtra(EXTRA_PANEL_PACKAGE_NAME, targetPackage)
            .putExtra(EXTRA_PACKAGE_NAME, targetPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        if (activities.none { it.isSystemApp() }) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun ResolveInfo.isSystemApp(): Boolean {
        val info = activityInfo?.applicationInfo ?: return false
        return info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
// KMK <--
