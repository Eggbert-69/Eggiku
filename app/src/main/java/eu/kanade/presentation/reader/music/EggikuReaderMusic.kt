package eu.kanade.presentation.reader.music

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.data.music.EggikuMusic
import eu.kanade.tachiyomi.data.music.EggikuMusicPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/** Eggiku: shared between the reader's mini player and the music button in its bottom bar */
@Stable
class EggikuReaderMusicState {
    /** Whether the player (capsule or note) is meant to be on screen */
    var shown by mutableStateOf(false)

    /** The series' reading playlist, or its category's */
    var playlistLink by mutableStateOf<String?>(null)

    var enabled by mutableStateOf(true)

    /** The music-note button in the reader menu */
    fun onMusicButton(context: Context, scope: CoroutineScope) {
        val preferences = Injekt.get<EggikuMusicPreferences>()
        val playing = EggikuMusic.nowPlaying.value
        val link = playlistLink
        when {
            playing?.isPlaying != true && link != null -> {
                shown = true
                preferences.miniPlayerCollapsed().set(false)
                scope.launch {
                    if (!EggikuMusic.openPlaylist(context, link)) context.toast(KMR.strings.music_playlist_open_failed)
                }
            }
            !EggikuMusic.isAccessGranted(context) -> {
                context.toast(KMR.strings.music_needs_access)
            }
            playing != null -> {
                shown = true
                preferences.miniPlayerCollapsed().set(false)
            }
            else -> context.toast(KMR.strings.music_nothing_playing)
        }
    }
}

@Composable
fun rememberEggikuReaderMusicState() = remember { EggikuReaderMusicState() }

/**
 * Eggiku: decides when the reader's mini player shows up.
 * - On its own when music is playing as a chapter opens, or when the series' playlist auto-starts.
 * - Then it follows the reader menu: shows when the menu opens, hides when it closes.
 */
@Composable
fun EggikuReaderMusic(
    state: EggikuReaderMusicState,
    menuVisible: Boolean,
    mangaId: Long?,
    chapterId: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { Injekt.get<EggikuMusicPreferences>() }
    val miniPlayer by preferences.miniPlayer().collectPrefAsState()
    val bounce by preferences.bounceWhenSettling().collectPrefAsState()
    val collapsed by preferences.miniPlayerCollapsed().collectPrefAsState()
    val nowPlaying by EggikuMusic.nowPlaying.collectAsState()
    state.enabled = miniPlayer

    // Work out the playlist: the series' own wins over its category's
    val seriesLink by remember(mangaId) {
        mangaId?.let { preferences.seriesPlaylist(it).changes() } ?: kotlinx.coroutines.flow.flowOf("")
    }.collectAsState("")
    val categoryNames by remember(mangaId) {
        mangaId?.let { id -> Injekt.get<GetCategories>().subscribe(id).map { list -> list.map { it.name } } }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    val categoryEntries by preferences.categoryPlaylists().collectPrefAsState()
    val link = remember(seriesLink, categoryNames, categoryEntries) {
        seriesLink.ifBlank {
            val map = EggikuMusicPreferences.parseCategoryPlaylists(categoryEntries)
            categoryNames.firstNotNullOfOrNull { map[it] }.orEmpty()
        }.takeIf { it.isNotBlank() }
    }
    state.playlistLink = link

    // Auto-start once per reading session
    var autoStarted by rememberSaveable(mangaId) { mutableStateOf(false) }
    LaunchedEffect(mangaId, link) {
        if (autoStarted || mangaId == null || link == null) return@LaunchedEffect
        if (!preferences.seriesAutoStart(mangaId).get()) return@LaunchedEffect
        autoStarted = true
        if (EggikuMusic.nowPlaying.value?.isPlaying == true) {
            state.shown = true
            return@LaunchedEffect
        }
        state.shown = true
        EggikuMusic.openPlaylist(context, link)
    }

    // A chapter opening with music already playing brings the player up
    LaunchedEffect(chapterId) {
        if (chapterId == null) return@LaunchedEffect
        delay(400) // let the media session report in
        if (EggikuMusic.nowPlaying.value?.isPlaying == true) state.shown = true
    }

    // After that, follow the reader menu
    var lastMenu by remember { mutableStateOf(menuVisible) }
    LaunchedEffect(menuVisible) {
        if (menuVisible != lastMenu) {
            lastMenu = menuVisible
            state.shown = menuVisible
        }
    }

    if (!miniPlayer) return

    val animation = rememberMiniPlayerAnimationSettings(bounce)
    val form = when {
        !state.shown -> MiniPlayerForm.HIDDEN
        collapsed -> MiniPlayerForm.COLLAPSED
        else -> MiniPlayerForm.EXPANDED
    }
    EggikuMiniPlayer(
        nowPlaying = nowPlaying,
        form = form,
        playlistName = null,
        animation = animation,
        onCollapse = { preferences.miniPlayerCollapsed().set(true) },
        onExpand = { preferences.miniPlayerCollapsed().set(false) },
        modifier = modifier,
    )
}
// KMK <--
