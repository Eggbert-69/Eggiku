package eu.kanade.tachiyomi.data.music

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KMK -->

/** Eggiku: settings for Reading music */
class EggikuMusicPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun miniPlayer() = preferenceStore.getBoolean("eggiku_music_mini_player", true)

    fun pauseWhenLeavingReader() = preferenceStore.getBoolean("eggiku_music_pause_on_leave", false)

    fun fadeOutAfter30Minutes() = preferenceStore.getBoolean("eggiku_music_fade_out", false)

    fun bounceWhenSettling() = preferenceStore.getBoolean("eggiku_music_bounce", true)

    /** Whether the reader's player is shrunk to the music note */
    fun miniPlayerCollapsed() = preferenceStore.getBoolean(
        Preference.appStateKey("eggiku_music_collapsed"),
        false,
    )

    /** Entries look like "categoryName\tplaylistUrl" */
    fun categoryPlaylists() = preferenceStore.getStringSet("eggiku_music_category_playlists", emptySet())

    fun seriesPlaylist(mangaId: Long) = preferenceStore.getString("eggiku_music_series_$mangaId", "")

    fun seriesAutoStart(mangaId: Long) = preferenceStore.getBoolean("eggiku_music_series_auto_$mangaId", false)

    fun categoryPlaylistMap(): Map<String, String> = parseCategoryPlaylists(categoryPlaylists().get())

    fun setCategoryPlaylist(categoryName: String, url: String?) {
        val map = categoryPlaylistMap().toMutableMap()
        if (url.isNullOrBlank()) map.remove(categoryName) else map[categoryName] = url.trim()
        categoryPlaylists().set(map.map { (name, link) -> "$name\t$link" }.toSet())
    }

    companion object {
        fun parseCategoryPlaylists(entries: Set<String>): Map<String, String> = entries
            .filter { '\t' in it }
            .associate { it.substringBefore('\t') to it.substringAfter('\t') }
    }
}
// KMK <--
