package eu.kanade.tachiyomi.data.backup

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// KMK -->

/**
 * Eggiku: works out whether backups are healthy and turns backup errors into plain language.
 */
object EggikuBackupHealth {

    enum class Level { GOOD, WARNING, PROBLEM }

    private val preferences: BackupPreferences by lazy { Injekt.get() }

    fun recordSuccess() {
        preferences.lastBackupError().set("")
        preferences.lastBackupErrorTime().set(0L)
    }

    /** Saves the failure for the settings screen and returns a short reason to show the user. */
    fun recordFailure(context: Context, error: Throwable): String {
        val reason = reasonFor(context, error)
        preferences.lastBackupError().set(reason)
        preferences.lastBackupErrorTime().set(System.currentTimeMillis())
        return reason
    }

    fun reasonFor(context: Context, error: Throwable): String {
        val message = error.message.orEmpty()
        val res = when {
            error is SecurityException || "permission" in message.lowercase() ->
                KMR.strings.backup_reason_no_access
            error is FileNotFoundException || "no such file" in message.lowercase() ->
                KMR.strings.backup_reason_folder_missing
            "ENOSPC" in message || "no space" in message.lowercase() ->
                KMR.strings.backup_reason_no_space
            "Couldn't open the cloud backup file" in message ->
                KMR.strings.backup_reason_cloud_file
            error is IOException && ("network" in message.lowercase() || "host" in message.lowercase()) ->
                KMR.strings.backup_reason_offline
            else -> null
        }
        return if (res != null) {
            context.stringResource(res)
        } else {
            context.stringResource(KMR.strings.backup_reason_other, message.ifBlank { error::class.simpleName.orEmpty() })
        }
    }

    /**
     * GOOD: a backup ran recently and the last copy worked.
     * WARNING: no backup for a while (more than twice the schedule, or 3 days when automatic backups are off).
     * PROBLEM: never backed up, or the last backup or cloud copy failed.
     */
    fun level(
        lastBackupTime: Long,
        backupIntervalHours: Int,
        lastError: String,
        cloudError: String,
        now: Long = System.currentTimeMillis(),
    ): Level {
        if (lastBackupTime <= 0L || lastError.isNotEmpty() || cloudError.isNotEmpty()) return Level.PROBLEM
        val allowed = if (backupIntervalHours > 0) {
            maxOf((backupIntervalHours * 2).hours, 1.days)
        } else {
            3.days
        }
        return if (now - lastBackupTime > allowed.inWholeMilliseconds) Level.WARNING else Level.GOOD
    }
}
// KMK <--
