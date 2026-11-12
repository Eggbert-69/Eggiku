package eu.kanade.tachiyomi.util

import android.net.Uri
import androidx.core.net.toUri

// KMK -->

/**
 * Eggiku: sharing a series with a friend. The message carries an eggiku:// link (opens the
 * series straight away in Eggiku) plus the normal web link. If the eggiku:// link isn't tappable
 * in a chat app, sharing the message to Eggiku works too: Eggiku pulls the link back out.
 */
object EggikuShare {

    private const val SCHEME = "eggiku"
    private const val HOST = "open"

    fun link(webUrl: String, title: String): String = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendQueryParameter("url", webUrl)
        .appendQueryParameter("title", title)
        .build()
        .toString()

    data class Target(val url: String, val title: String?)

    fun fromUri(uri: Uri?): Target? {
        if (uri == null || uri.scheme != SCHEME) return null
        val url = uri.getQueryParameter("url")?.takeIf { it.isNotBlank() } ?: return null
        return Target(url, uri.getQueryParameter("title"))
    }

    /** Finds an Eggiku link (or, failing that, a web link) inside shared text */
    fun fromText(text: String?): Target? {
        if (text.isNullOrBlank()) return null
        Regex("""eggiku://\S+""").find(text)?.let { match ->
            fromUri(match.value.toUri())?.let { return it }
        }
        val web = Regex("""https?://\S+""").find(text)?.value ?: return null
        // Only pull a link out of a longer message; a bare link already works the old way
        if (web == text.trim()) return null
        return Target(web.trimEnd('.', ',', ')', '!'), null)
    }
}
// KMK <--
