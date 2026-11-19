package eu.kanade.tachiyomi.extension

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.model.findAvailable
import eu.kanade.tachiyomi.extension.model.isOutdatedComparedTo
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.workManager
import exh.log.xLogE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// KMK -->
/**
 * Eggiku: updates extensions in the background (on Wi-Fi by default), so the update badge rarely
 * shows. Works with the Private installer, and with the default package installer on Android 12+
 * for extensions Eggiku installed itself; anything that needs a tap is left for the usual
 * "updates available" notification.
 */
object EggikuExtensionAutoUpdate {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun enabled(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_ext_auto_update", true)

    fun wifiOnly(): Preference<Boolean> = preferenceStore.getBoolean("eggiku_ext_auto_update_wifi", true)

    /** When it last ran and what happened, for the settings line */
    fun lastRun(): Preference<Long> = preferenceStore.getLong(Preference.appStateKey("eggiku_ext_auto_update_last"), 0L)

    fun lastUpdated(): Preference<Int> = preferenceStore.getInt(Preference.appStateKey("eggiku_ext_auto_update_count"), 0)

    private const val TAG = "EggikuExtensionAutoUpdate"
    private const val ID_DONE = -2101

    /** Whether this phone's installer setting can update without a tap */
    fun canUpdateSilently(): Boolean = when (Injekt.get<BasePreferences>().extensionInstaller().get()) {
        BasePreferences.ExtensionInstaller.PRIVATE -> true
        BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> false
    }

    /** [on] and [wifi] let a settings switch pass its new value before it's saved */
    fun setup(context: Context, on: Boolean = enabled().get(), wifi: Boolean = wifiOnly().get()) {
        if (!on) {
            context.workManager.cancelUniqueWork(TAG)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS, 2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!enabled().get() || !canUpdateSilently()) return Result.success()
            return try {
                val updated = runOnce(applicationContext)
                lastRun().set(System.currentTimeMillis())
                lastUpdated().set(updated.size)
                if (updated.isNotEmpty()) notifyDone(updated)
                Result.success()
            } catch (e: Exception) {
                xLogE("Extension auto-update failed", e)
                Result.retry()
            }
        }

        private fun notifyDone(names: List<String>) {
            applicationContext.notify(ID_DONE, Notifications.CHANNEL_EXTENSIONS_UPDATE) {
                setSmallIcon(R.drawable.ic_extension_24dp)
                setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
                setContentTitle(
                    applicationContext.pluralStringResource(KMR.plurals.ext_auto_updated, names.size, names.size),
                )
                setContentText(names.joinToString(", "))
                setContentIntent(NotificationReceiver.openExtensionsPendingActivity(applicationContext))
                setSilent(true)
                setAutoCancel(true)
            }
        }
    }

    /** Checks the repos and updates what it can; returns the names it updated */
    suspend fun runOnce(context: Context): List<String> {
        val manager = Injekt.get<ExtensionManager>()
        manager.findAvailableExtensions()
        val available = manager.availableExtensionsFlow.first()
        // Read straight from the phone: the shared installed list may not be live in the background
        val installed = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }
        val updated = mutableListOf<String>()
        installed.forEach { ext ->
            val target = ext.findAvailable(available) ?: return@forEach
            if (!ext.isOutdatedComparedTo(target)) return@forEach
            if (update(context, ext, target)) updated += ext.name
        }
        if (updated.isNotEmpty()) {
            // Refresh badges and the "updates available" notification for anything left over
            manager.findAvailableExtensions()
        }
        return updated
    }

    private suspend fun update(context: Context, installed: Extension.Installed, target: Extension.Available): Boolean {
        val file = File(context.cacheDir, "eggiku_auto_${installed.pkgName}.apk")
        try {
            Injekt.get<NetworkHelper>().client.newCall(Request.Builder().url(target.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return false
                file.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } }
            }
            // Same check as a manual update: never replace an extension with one signed by someone else
            if (!ExtensionLoader.isSignedLikeInstalled(context, file)) return false
            return when (Injekt.get<BasePreferences>().extensionInstaller().get()) {
                BasePreferences.ExtensionInstaller.PRIVATE -> ExtensionLoader.installPrivateExtensionFile(context, file)
                BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> installSilently(context, file)
                else -> false
            }
        } catch (e: Exception) {
            xLogE("Auto-update of ${installed.name} failed", e)
            return false
        } finally {
            file.delete()
        }
    }

    /**
     * Installs through the system package installer without asking. Android only allows that on 12+
     * for apps Eggiku installed itself; if it wants a tap, the session is dropped and the normal
     * update notification takes over.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun installSilently(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        val action = "${context.packageName}.EGGIKU_AUTO_UPDATE.$sessionId"
        val result = withTimeoutOrNull(120_000L) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            // Needs a tap; leave it for the regular notification
                            runCatching { installer.abandonSession(sessionId) }
                        }
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(status == PackageInstaller.STATUS_SUCCESS)
                    }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                try {
                    installer.openSession(sessionId).use { session ->
                        session.openWrite("extension", 0, file.length()).use { out ->
                            file.inputStream().use { it.copyTo(out) }
                            session.fsync(out)
                        }
                        val pending = PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            Intent(action).setPackage(context.packageName),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                        )
                        session.commit(pending.intentSender)
                    }
                } catch (e: Exception) {
                    xLogE("Silent install failed", e)
                    runCatching { installer.abandonSession(sessionId) }
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
        if (result == null) runCatching { installer.abandonSession(sessionId) }
        return result == true
    }
}
// KMK <--
