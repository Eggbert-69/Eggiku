package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.EggikuCloudBackup
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.FeedBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SavedSearchBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),

    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator = ExtensionStoresBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    // KMK -->
    private val feedBackupCreator: FeedBackupCreator = FeedBackupCreator(),
    // KMK <--
    // SY -->
    private val savedSearchBackupCreator: SavedSearchBackupCreator = SavedSearchBackupCreator(),
    private val getMergedManga: GetMergedManga = Injekt.get(),
    // SY <--
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        // KMK -->
        var dir: UniFile? = null
        // KMK <--
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                dir = UniFile.fromUri(context, uri)

                // KMK -->
                // Older backups are pruned after the new one is written successfully
                // KMK <--

                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            // SY -->
            val mergedManga = getMergedManga.await()
            // SY <--
            val backupManga =
                backupMangas(getFavorites.await() + nonFavoriteManga /* SY --> */ + mergedManga /* SY <-- */, options)

            val backup = Backup(
                backupManga = backupManga,
                backupCategories = backupCategories(options),
                backupSources = backupSources(backupManga),
                backupPreferences = backupAppPreferences(options),
                backupExtensionStores = backupExtensionStores(options),
                backupSourcePreferences = backupSourcePreferences(options),

                // SY -->
                backupSavedSearches = backupSavedSearches(options),
                // SY <--

                // KMK -->
                backupFeeds = backupFeeds(options),
                // KMK <--
            )

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            // KMK -->
            val hash = MessageDigest.getInstance("SHA-256").digest(byteArray).joinToString("") { "%02x".format(it) }
            if (isAutoBackup &&
                backupPreferences.skipUnchangedBackups().get() &&
                hash == backupPreferences.lastBackupHash().get()
            ) {
                // Nothing changed since the last backup: keep the existing files instead of piling up copies
                file.delete()
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
                return ""
            }
            // KMK <--

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use {
                    it.write(byteArray)
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            // KMK -->
            backupPreferences.lastBackupHash().set(hash)
            backupPreferences.lastBackupTime().set(Instant.now().toEpochMilli())
            backupPreferences.lastBackupSize().set(file.length())
            backupPreferences.lastBackupEntries().set(backupManga.count { it.favorite })
            backupPreferences.lastBackupWasAuto().set(isAutoBackup)

            if (isAutoBackup) {
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.lastModified() }
                    .drop(backupPreferences.autoBackupsToKeep().get().coerceAtLeast(1))
                    .forEach { it.delete() }
            }

            EggikuCloudBackup.copy(context, file)
            // KMK <--

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    suspend fun backupMangas(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (!options.libraryEntries) return emptyList()

        return mangaBackupCreator(mangas, options)
    }

    fun backupSources(mangas: List<BackupManga>): List<BackupSource> {
        return sourcesBackupCreator(mangas)
    }

    internal fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    internal suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    // SY -->
    suspend fun backupSavedSearches(options: BackupOptions): List<BackupSavedSearch> {
        if (!options.savedSearchesFeeds) return emptyList()

        return savedSearchBackupCreator()
    }
    // SY <--

    // KMK -->
    /**
     * Backup global Popular/Latest feeds
     */
    suspend fun backupFeeds(options: BackupOptions): List<BackupFeed> {
        if (!options.savedSearchesFeeds) return emptyList()

        return feedBackupCreator()
    }
    // KMK <--

    companion object {
        // KMK -->
        // Eggiku names backups "Eggiku_<date>.tachibk"; older app-ID-named backups still count toward rotation
        private val FILENAME_REGEX =
            """(Eggiku|${Regex.escape(BuildConfig.APPLICATION_ID)})_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "Eggiku_$date.tachibk"
        }
        // KMK <--
    }
}
