package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: keeps a copy of the newest backup in a file the user picked once,
 * usually in Google Drive. Cloud apps keep older versions of the file themselves.
 */
object EggikuCloudBackup {

    private val backupPreferences: BackupPreferences by lazy { Injekt.get() }

    fun targetUri(): Uri? = backupPreferences.cloudCopyUri().get().takeIf { it.isNotBlank() }?.toUri()

    /** Copies [source] over the chosen cloud file. Never throws: failures are recorded for the settings screen. */
    fun copy(context: Context, source: UniFile) {
        val target = targetUri() ?: return
        try {
            val input = source.openInputStream()
            input.use {
                val output = try {
                    context.contentResolver.openOutputStream(target, "wt")
                } catch (_: Exception) {
                    // Some providers don't support truncate mode
                    context.contentResolver.openOutputStream(target, "w")
                } ?: throw IllegalStateException("Couldn't open the cloud backup file")
                output.use { input.copyTo(it) }
            }
            backupPreferences.cloudCopyLastTime().set(System.currentTimeMillis())
            backupPreferences.cloudCopyLastError().set("")
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Eggiku: couldn't copy backup to cloud" }
            val reason = EggikuBackupHealth.reasonFor(context, e)
            backupPreferences.cloudCopyLastError().set(reason)
            try {
                BackupNotifier(context).showCloudCopyError(reason)
            } catch (_: Exception) {
                // Notifications may be off
            }
        }
    }

    /** Copies the newest automatic backup, e.g. right after the user picks a cloud file. */
    fun copyLatest(context: Context): Boolean {
        val latest = Injekt.get<StorageManager>().getAutomaticBackupsDirectory()
            ?.listFiles()
            ?.filter { it.name?.endsWith(".tachibk") == true }
            ?.maxByOrNull { it.lastModified() }
            ?: return false
        copy(context, latest)
        return backupPreferences.cloudCopyLastError().get().isEmpty()
    }

    /** "Google Drive", "Dropbox"... from the document provider, or null when unknown. */
    fun providerName(uri: Uri): String? {
        val authority = uri.authority.orEmpty()
        return when {
            "com.google.android.apps.docs" in authority -> "Google Drive"
            "dropbox" in authority -> "Dropbox"
            "skydrive" in authority || "onedrive" in authority -> "OneDrive"
            "nextcloud" in authority -> "Nextcloud"
            "com.android.externalstorage" in authority -> null
            else -> null
        }
    }

    fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Turns a storage access URI into something like "Internal storage/Komikku/autobackup". */
    fun readablePath(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme == "file") return uri.path
        val documentId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) {
            try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                return uri.toString()
            }
        }
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':')
        val volumeName = when {
            volume.equals("primary", ignoreCase = true) -> "Internal storage"
            volume.isEmpty() -> return documentId
            else -> "SD card ($volume)"
        }
        return if (path.isBlank()) volumeName else "$volumeName/$path"
    }
}
// KMK <--
