package tachiyomi.domain.backup.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class BackupPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun backupInterval() = preferenceStore.getInt("backup_interval", 12)

    fun lastAutoBackupTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_auto_backup_timestamp"), 0L)

    // KMK -->
    fun showRestoringProgressBanner() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_restoring_progress_banner_key"),
        true,
    )

    fun autoBackupsToKeep() = preferenceStore.getInt("eggiku_auto_backups_to_keep", 4)

    fun skipUnchangedBackups() = preferenceStore.getBoolean("eggiku_skip_unchanged_backups", true)

    fun lastBackupHash() = preferenceStore.getString(Preference.appStateKey("eggiku_last_backup_hash"), "")

    fun lastBackupTime() = preferenceStore.getLong(Preference.appStateKey("eggiku_last_backup_time"), 0L)

    fun lastBackupSize() = preferenceStore.getLong(Preference.appStateKey("eggiku_last_backup_size"), 0L)

    fun lastBackupEntries() = preferenceStore.getInt(Preference.appStateKey("eggiku_last_backup_entries"), 0)

    fun lastBackupWasAuto() = preferenceStore.getBoolean(Preference.appStateKey("eggiku_last_backup_auto"), false)

    fun cloudCopyUri() = preferenceStore.getString(Preference.appStateKey("eggiku_cloud_copy_uri"), "")

    fun cloudCopyLastTime() = preferenceStore.getLong(Preference.appStateKey("eggiku_cloud_copy_time"), 0L)

    fun cloudCopyLastError() = preferenceStore.getString(Preference.appStateKey("eggiku_cloud_copy_error"), "")

    fun lastBackupError() = preferenceStore.getString(Preference.appStateKey("eggiku_last_backup_error"), "")

    fun lastBackupErrorTime() = preferenceStore.getLong(Preference.appStateKey("eggiku_last_backup_error_time"), 0L)
    // KMK <--
}
