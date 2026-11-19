package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import exh.log.xLogE
import exh.log.xLogI
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// KMK -->
/**
 * Eggiku Fold sync: keeps your library, reading progress and categories in step across your
 * phones through one file in Google Drive (or any cloud app that shows up in Android's file
 * picker). Each phone picks the same file once; every sync reads it, merges it with what's on
 * this phone the same way the other sync services do, and writes the result back.
 *
 * No Google sign-in or server is needed, so it works in Eggiku builds.
 */
class EggikuDriveFileSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    override suspend fun doSync(syncData: SyncData): Backup? = withIOContext {
        val uri = fileUri()
        if (uri == null) {
            notifier.showSyncError(NO_FILE_MESSAGE)
            return@withIOContext null
        }
        try {
            val remote = read(uri)
            val merged = if (remote != null) mergeSyncData(syncData, remote) else syncData
            write(uri, merged)
            lastSynced().set(System.currentTimeMillis())
            lastError().set("")
            xLogI("Eggiku Drive file sync done")
            merged.backup
        } catch (e: Exception) {
            xLogE("Eggiku Drive file sync failed", e)
            lastError().set(e.message ?: e::class.simpleName.orEmpty())
            notifier.showSyncError(e.message)
            null
        }
    }

    private fun read(uri: Uri): SyncData? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        return try {
            SyncData(backup = protoBuf.decodeFromByteArray(Backup.serializer(), raw))
        } catch (e: Exception) {
            // Never write over a file that has something else in it
            throw IllegalStateException("The chosen sync file isn't an Eggiku sync file. Pick another one.", e)
        }
    }

    private fun write(uri: Uri, data: SyncData) {
        val backup = data.backup ?: return
        val raw = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        check(raw.isNotEmpty()) { "Nothing to sync" }
        val packed = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(raw) }
            out.toByteArray()
        }
        val output = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            // Some providers don't support truncate mode
            context.contentResolver.openOutputStream(uri, "w")
        } ?: error("Couldn't open the sync file")
        output.use { it.write(packed) }
    }

    companion object {
        private const val NO_FILE_MESSAGE = "Pick a sync file in Settings → Data and storage → Sync first"

        private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

        /** Kept out of backups: each phone has its own permission to the file */
        fun fileUriPref(): Preference<String> = preferenceStore.getString(Preference.appStateKey("eggiku_sync_file_uri"), "")

        fun lastSynced(): Preference<Long> = preferenceStore.getLong(Preference.appStateKey("eggiku_sync_file_last"), 0L)

        fun lastError(): Preference<String> = preferenceStore.getString(Preference.appStateKey("eggiku_sync_file_error"), "")

        fun fileUri(): Uri? = fileUriPref().get().takeIf { it.isNotBlank() }?.toUri()

        /** The file's name as the cloud app shows it, for the settings screen */
        fun fileName(context: Context): String? {
            val uri = fileUri() ?: return null
            return try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
// KMK <--
