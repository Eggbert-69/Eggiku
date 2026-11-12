package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: finds the extensions your library needs but aren't installed (for example right after
 * restoring a backup on a new phone) and installs them from your extension repos.
 */
object EggikuMissingExtensions {

    data class Report(
        /** Extensions in your repos that provide a missing source */
        val installable: List<Extension.Available>,
        /** Sources your library uses that no repo provides */
        val notFound: Int,
    )

    suspend fun find(): Report {
        val sourceManager = Injekt.get<SourceManager>()
        val extensionManager = Injekt.get<ExtensionManager>()

        val missingIds = Injekt.get<GetFavorites>().await()
            .map { it.source }
            .distinct()
            .filter { id -> sourceManager.get(id) == null && id > 0 }
            .toSet()
        if (missingIds.isEmpty()) return Report(emptyList(), 0)

        try {
            extensionManager.findAvailableExtensions()
        } catch (_: Exception) {
            // Offline: use what's cached
        }
        val available = extensionManager.availableExtensionsFlow.first()
        val installable = available.filter { ext -> ext.sources.any { it.id in missingIds } }
        val covered = installable.flatMap { ext -> ext.sources.map { it.id } }.toSet()
        return Report(installable, missingIds.count { it !in covered })
    }

    /** Installs one after another; each one may ask the system to confirm. Returns how many finished. */
    suspend fun installAll(extensions: List<Extension.Available>): Int {
        val extensionManager = Injekt.get<ExtensionManager>()
        var installed = 0
        extensions.forEach { ext ->
            try {
                var last: eu.kanade.tachiyomi.extension.model.InstallStep? = null
                extensionManager.installExtension(ext)
                    .takeWhile { step ->
                        last = step
                        !step.isCompleted()
                    }
                    .collect {}
                if (last == eu.kanade.tachiyomi.extension.model.InstallStep.Installed) installed++
            } catch (_: Exception) {
                // Skip this one and carry on
            }
        }
        return installed
    }
}
// KMK <--
