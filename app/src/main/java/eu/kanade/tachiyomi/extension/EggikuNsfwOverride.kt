package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: your own 18+ rating per extension. Some extensions are marked 18+ by their repo but
 * are only mature, or the other way round; what you set here wins over the repo everywhere
 * (the egg button, the extension list, sources and global search).
 */
object EggikuNsfwOverride {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Entries look like "pkgName:1" (18+) or "pkgName:0" (not 18+) */
    fun overrides(): Preference<Set<String>> = preferenceStore.getStringSet("eggiku_nsfw_overrides", emptySet())

    fun parse(entries: Set<String>): Map<String, Boolean> = entries.mapNotNull {
        val pkg = it.substringBeforeLast(':', "")
        if (pkg.isEmpty()) return@mapNotNull null
        pkg to (it.substringAfterLast(':') == "1")
    }.toMap()

    /** Your setting if you made one, otherwise null */
    fun overrideFor(pkgName: String, entries: Set<String> = overrides().get()): Boolean? = parse(entries)[pkgName]

    fun isNsfw(extension: Extension, entries: Set<String> = overrides().get()): Boolean =
        overrideFor(extension.pkgName, entries) ?: extension.isNsfw

    /** Set to the repo's own rating to clear the override */
    fun set(extension: Extension, nsfw: Boolean) {
        val map = parse(overrides().get()).toMutableMap()
        if (nsfw == extension.isNsfw) map.remove(extension.pkgName) else map[extension.pkgName] = nsfw
        overrides().set(map.map { (pkg, value) -> "$pkg:${if (value) 1 else 0}" }.toSet())
    }
}
// KMK <--
