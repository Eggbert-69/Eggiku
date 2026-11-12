package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.first
import mihon.domain.extension.interactor.GetExtensionStores
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

private enum class OrphanReason { REPO_REMOVED, NOT_LISTED, SIGNATURE_MISMATCH }

private data class OrphanInfo(
    val reason: OrphanReason,
    val seriesCount: Int,
    /** Source with the most library entries, for migrating */
    val busiestSourceId: Long?,
    /** A same-named extension from one of your repos */
    val replacement: Extension.Available?,
)

/**
 * Eggiku: explains why an extension is orphaned (instead of the generic warning) and offers fixes.
 */
@Composable
fun EggikuOrphanBanner(
    extension: Extension.Installed,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState<OrphanInfo?>(null, extension.pkgName, extension.signatureHash) {
        value = withIOContext { findOrphanInfo(extension) }
    }

    val reasonText = when (info?.reason) {
        OrphanReason.REPO_REMOVED -> stringResource(KMR.strings.orphan_reason_repo_removed)
        OrphanReason.NOT_LISTED -> stringResource(KMR.strings.orphan_reason_not_listed)
        OrphanReason.SIGNATURE_MISMATCH -> stringResource(KMR.strings.orphan_reason_signature)
        null -> stringResource(KMR.strings.orphan_reason_checking)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(KMR.strings.orphan_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = reasonText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        info?.let { orphan ->
            orphan.replacement?.takeIf { it.pkgName == extension.pkgName }?.let { replacement ->
                Text(
                    text = stringResource(KMR.strings.orphan_reinstall_hint, replacement.storeName),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = if (orphan.seriesCount > 0) {
                    pluralStringResource(KMR.plurals.orphan_series_count, orphan.seriesCount, orphan.seriesCount)
                } else {
                    stringResource(KMR.strings.orphan_series_none)
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                orphan.replacement?.takeIf { it.pkgName != extension.pkgName }?.let { replacement ->
                    FilledTonalButton(
                        onClick = {
                            context.toast(KMR.strings.orphan_installing)
                            scope.launchIO {
                                Injekt.get<ExtensionManager>().installExtension(replacement).collect {}
                            }
                        },
                    ) {
                        Text(stringResource(KMR.strings.orphan_install_from, replacement.storeName))
                    }
                }
                if (orphan.reason == OrphanReason.REPO_REMOVED) {
                    FilledTonalButton(onClick = { navigator?.push(ExtensionStoresScreen()) }) {
                        Text(stringResource(KMR.strings.orphan_readd_repo))
                    }
                }
                orphan.busiestSourceId?.let { sourceId ->
                    FilledTonalButton(onClick = { navigator?.push(MigrateMangaScreen(sourceId)) }) {
                        Text(stringResource(KMR.strings.orphan_migrate))
                    }
                }
            }
        }
    }
}

private suspend fun findOrphanInfo(extension: Extension.Installed): OrphanInfo {
    val extensionManager = Injekt.get<ExtensionManager>()
    val stores = try {
        Injekt.get<GetExtensionStores>().get()
    } catch (_: Exception) {
        emptyList()
    }
    val available = extensionManager.availableExtensionsFlow.first()

    val samePackage = available.firstOrNull { it.pkgName == extension.pkgName }
    val reason = when {
        samePackage != null && samePackage.signatureHash != extension.signatureHash -> OrphanReason.SIGNATURE_MISMATCH
        stores.none { it.signingKey == extension.signatureHash } -> OrphanReason.REPO_REMOVED
        else -> OrphanReason.NOT_LISTED
    }

    val sourceIds = extension.sources.map { it.id }.toSet()
    val bySource = Injekt.get<GetFavorites>().await()
        .filter { it.source in sourceIds }
        .groupingBy { it.source }
        .eachCount()

    val replacement = samePackage
        ?: available.firstOrNull {
            it.name.equals(extension.name, ignoreCase = true) && it.lang == extension.lang
        }

    return OrphanInfo(
        reason = reason,
        seriesCount = bySource.values.sum(),
        busiestSourceId = bySource.maxByOrNull { it.value }?.key,
        replacement = replacement,
    )
}
// KMK <--
