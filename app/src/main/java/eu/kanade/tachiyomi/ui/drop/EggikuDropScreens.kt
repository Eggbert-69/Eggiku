package eu.kanade.tachiyomi.ui.drop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.avatar.EggikuAvatarImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.drop.EggikuDrops
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.eggikuPop
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
private val StarColor = Color(0xFFFFB81C)

/**
 * Opens a dropped series: straight to the series page when the friend's source is installed,
 * otherwise tries the web link and falls back to searching every source for the title.
 */
private fun openDrop(drop: EggikuDrops.Drop, navigator: Navigator, scope: CoroutineScope, onError: (String?) -> Unit) {
    scope.launch {
        val source = Injekt.get<SourceManager>().get(drop.sourceId)
        if (source == null || drop.url.isBlank()) {
            navigator.push(DeepLinkScreen(drop.webUrl ?: drop.title, drop.title))
            return@launch
        }
        val manga = try {
            withIOContext {
                Injekt.get<NetworkToLocalManga>()(
                    Manga.create().copy(
                        url = drop.url,
                        ogTitle = drop.title,
                        ogThumbnailUrl = drop.cover,
                        source = drop.sourceId,
                    ),
                    updateInfo = false,
                )
            }
        } catch (e: Exception) {
            xLogE("Egg Drop: couldn't open ${drop.title}", e)
            onError(e.message)
            null
        }
        if (manga != null) {
            navigator.push(MangaScreen(manga.id, true))
        } else {
            navigator.push(DeepLinkScreen(drop.webUrl ?: drop.title, drop.title))
        }
    }
}

/** A drop a friend just sent: saved to the inbox, shown big, one tap to open */
class EggikuDropScreen(private val drop: EggikuDrops.Drop) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            withIOContext {
                EggikuDrops.receive(drop)
                EggikuDrops.markSeen(EggikuDrops.Received(drop, 0L).key)
            }
        }

        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }
        val installed = remember { Injekt.get<SourceManager>().get(drop.sourceId) != null }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.egg_drop_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Sender(drop, big = true)
                MangaCover.Book(
                    data = drop.cover,
                    modifier = Modifier
                        .width(140.dp)
                        .eggikuPop(drop.title),
                )
                Text(drop.title, style = MaterialTheme.typography.titleLarge)
                if (drop.stars > 0) Stars(drop.stars)
                if (drop.note.isNotBlank()) {
                    Text(
                        text = "“${drop.note}”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                    )
                }
                Text(
                    text = if (installed) {
                        stringResource(KMR.strings.egg_drop_on_source, drop.sourceName)
                    } else {
                        stringResource(KMR.strings.egg_drop_source_missing, drop.sourceName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { openDrop(drop, navigator, scope) { context.toast(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (installed) KMR.strings.egg_drop_open else KMR.strings.egg_drop_find,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = { navigator.replace(EggikuDropInboxScreen()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(KMR.strings.egg_drop_inbox))
                }
            }
        }
    }
}

/** Every series friends have dropped you, newest first */
class EggikuDropInboxScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val inbox by remember { EggikuDrops.inboxChanges() }.collectAsState(EggikuDrops.inbox())
        var pendingDelete by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.egg_drop_inbox),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (inbox.any { !it.seen }) {
                            IconButton(onClick = { scope.launch { withIOContext { EggikuDrops.markAllSeen() } } }) {
                                Icon(
                                    Icons.Outlined.DoneAll,
                                    contentDescription = stringResource(KMR.strings.egg_drop_mark_all_seen),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (inbox.isEmpty()) {
                EmptyScreen(
                    message = stringResource(KMR.strings.egg_drop_inbox_empty),
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(inbox, key = { it.key }) { received ->
                    InboxRow(
                        received = received,
                        confirmingDelete = pendingDelete == received.key,
                        onClick = {
                            scope.launch { withIOContext { EggikuDrops.markSeen(received.key) } }
                            openDrop(received.drop, navigator, scope) { context.toast(it) }
                        },
                        onDelete = {
                            if (pendingDelete == received.key) {
                                pendingDelete = null
                                scope.launch { withIOContext { EggikuDrops.remove(received.key) } }
                            } else {
                                // First tap arms it, second tap deletes, so a stray tap can't lose a drop
                                pendingDelete = received.key
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    @Composable
    private fun InboxRow(
        received: EggikuDrops.Received,
        confirmingDelete: Boolean,
        onClick: () -> Unit,
        onDelete: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val drop = received.drop
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                MangaCover.Book(data = drop.cover, modifier = Modifier.width(56.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = drop.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!received.seen) {
                            Badge(modifier = Modifier.padding(start = 6.dp)) {
                                Text(stringResource(KMR.strings.egg_drop_new))
                            }
                        }
                    }
                    Sender(drop, big = false)
                    if (drop.stars > 0) Stars(drop.stars, small = true)
                    if (drop.note.isNotBlank()) {
                        Text(
                            text = "“${drop.note}”",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = relativeDateText(received.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(KMR.strings.egg_drop_delete),
                        tint = if (confirmingDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.eggikuPop(confirmingDelete),
                    )
                }
            }
        }
    }
}

@Composable
private fun Sender(drop: EggikuDrops.Drop, big: Boolean) {
    val name = drop.from.ifBlank { stringResource(KMR.strings.compare_friend) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val avatar = drop.avatar
        if (avatar != null) {
            EggikuAvatarImage(avatar = avatar, size = if (big) 40.dp else 24.dp)
        } else {
            Box(Modifier.size(if (big) 40.dp else 24.dp), contentAlignment = Alignment.Center) {
                Text("🥚")
            }
        }
        Text(
            text = stringResource(KMR.strings.egg_drop_from, name),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Stars(stars: Int, small: Boolean = false) {
    Row {
        (1..5).forEach { star ->
            Icon(
                imageVector = if (star <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (star <= stars) StarColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (small) 16.dp else 24.dp),
            )
        }
    }
}
// KMK <--
