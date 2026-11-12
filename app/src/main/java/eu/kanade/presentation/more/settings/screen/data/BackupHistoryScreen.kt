package eu.kanade.presentation.more.settings.screen.data

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.EggikuCloudBackup
import eu.kanade.tachiyomi.util.system.toShareIntent
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.IconButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: lists the automatic backups saved on the phone, newest first.
 * Tap one to restore it, or share it somewhere else.
 */
class BackupHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val folder by produceState<UniFile?>(initialValue = null) {
            value = withIOContext { Injekt.get<StorageManager>().getAutomaticBackupsDirectory() }
        }
        val backups by produceState(initialValue = emptyList<UniFile>(), folder) {
            value = withIOContext {
                folder?.listFiles()
                    ?.filter { it.name?.endsWith(".tachibk") == true }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.backup_history_title),
                    subtitle = EggikuCloudBackup.readablePath(folder?.uri),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(KMR.strings.backup_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(MaterialTheme.padding.medium),
                )
                return@Scaffold
            }

            LazyColumn(contentPadding = contentPadding) {
                item {
                    Text(
                        text = stringResource(KMR.strings.backup_history_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                    )
                }
                items(
                    items = backups,
                    key = { it.uri.toString() },
                ) { file ->
                    val modified = file.lastModified()
                    ListItem(
                        headlineContent = { Text(file.name.orEmpty()) },
                        supportingContent = {
                            Text(
                                DateUtils.formatDateTime(
                                    context,
                                    modified,
                                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                                ) + " · " + Formatter.formatShortFileSize(context, file.length()),
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        file.uri.toShareIntent(context, type = "application/x-protobuf+gzip"),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = stringResource(KMR.strings.backup_history_share),
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            navigator.push(RestoreBackupScreen(file.uri.toString()))
                        },
                    )
                }
            }
        }
    }
}
// KMK <--
