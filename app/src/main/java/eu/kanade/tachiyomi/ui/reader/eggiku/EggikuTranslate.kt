package eu.kanade.tachiyomi.ui.reader.eggiku

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import exh.log.xLogE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

// KMK -->
/**
 * Eggiku raw translation, as a toggle. While it's on, every page gets read once as it comes on
 * screen, its speech bubbles are translated, and the translations stay pinned to the page, so
 * you can keep scrolling, zooming and turning pages. Results are remembered per page.
 *
 * Everything runs on the phone with Google's ML Kit: text recognition comes through Play services
 * and each language pack (about 30 MB) downloads the first time it's needed.
 */
object EggikuTranslate {

    /** A translated bubble, in the coordinates of whatever image it was found in */
    data class Bubble(
        val rect: RectF,
        val original: String,
        val translated: String,
        val vertical: Boolean,
    )

    sealed interface Status {
        data object Idle : Status
        data object Downloading : Status
        data class Failed(val message: String?) : Status
    }

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val activePref by lazy { preferenceStore.getBoolean("eggiku_translate_on", false) }

    /** "auto", or a language code like "ja", "ko", "zh" */
    fun rawLanguage() = preferenceStore.getString("eggiku_translate_from", "auto")

    /** Download language packs on mobile data too */
    fun allowMobileData() = preferenceStore.getBoolean("eggiku_translate_mobile_data", true)

    /** Font for translations: an [eu.kanade.presentation.theme.EggikuFont] name, or "APP" to match the app font */
    fun fontPref() = preferenceStore.getString("eggiku_translate_font", "COMIC_NEUE")

    fun boldPref() = preferenceStore.getBoolean("eggiku_translate_bold", true)

    /** Relays out every translated page with the new font, without reading the pages again */
    fun fontChanged() {
        pageCache.evictAll()
        pages.toList().forEach { it.relayout() }
    }

    private val mutableActive by lazy { MutableStateFlow(activePref.get()) }
    val active: StateFlow<Boolean> by lazy { mutableActive.asStateFlow() }

    private val mutableStatus = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    /** True for the moment the screen is being read, so Eggiku's own chips stay out of it */
    private val mutableCapturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = mutableCapturing.asStateFlow()

    /** Reader menus cover the page; nothing is read while they're open */
    @Volatile
    var menuVisible = false
        set(value) {
            val wasVisible = field
            field = value
            if (wasVisible && !value) refreshAll()
        }

    /** The series' source language, for "auto" */
    var sourceLangProvider: (() -> String?)? = null

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One page is read at a time, so scrolling stays smooth */
    private val ocrMutex = Mutex()

    private val pages = Collections.newSetFromMap(WeakHashMap<EggikuPageTranslation, Boolean>())

    /** Finished pages by page key, so a page you come back to shows instantly */
    internal val pageCache = LruCache<String, EggikuPageTranslation.Saved>(150)

    init {
        // Settings changes apply right away to pages already translated
        scope.launch { merge(fontPref().changes().drop(1), boldPref().changes().drop(1)).collect { fontChanged() } }
        scope.launch {
            rawLanguage().changes().drop(1).collect {
                pageCache.evictAll()
                autoLang = null
                pages.toList().forEach {
                    it.reset()
                    it.schedule(150)
                }
            }
        }
    }

    private val translationCache = LruCache<String, String>(1000)
    private val recognizers = mutableMapOf<String, TextRecognizer>()
    private var translator: Pair<String, Translator>? = null
    private var autoLang: String? = null

    fun toggle(): Boolean {
        val on = !mutableActive.value
        mutableActive.value = on
        activePref.set(on)
        mutableStatus.value = Status.Idle
        if (on) refreshAll() else pages.toList().forEach { it.hide() }
        return on
    }

    internal fun register(page: EggikuPageTranslation) {
        pages += page
    }

    internal fun unregister(page: EggikuPageTranslation) {
        pages -= page
    }

    fun refreshAll() {
        pages.toList().forEach { it.schedule(150) }
    }

    internal fun setCapturing(value: Boolean) {
        mutableCapturing.value = value
    }

    internal fun fail(message: String?) {
        mutableStatus.value = Status.Failed(message)
    }

    fun clearFailure() {
        if (mutableStatus.value is Status.Failed) mutableStatus.value = Status.Idle
    }

    /** Frees the language models when the reader closes; translations stay cached */
    fun release() {
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
        translator?.second?.let { runCatching { it.close() } }
        translator = null
        autoLang = null
        sourceLangProvider = null
        mutableStatus.value = Status.Idle
    }

    // region Reading and translating

    private fun pickLanguage(): String? {
        val chosen = rawLanguage().get()
        val lang = if (chosen == "auto") sourceLangProvider?.invoke() else chosen
        return when {
            lang == null -> null
            lang.startsWith("ja") -> "ja"
            lang.startsWith("ko") -> "ko"
            lang.startsWith("zh") -> "zh"
            lang == "all" || lang == "other" -> null
            else -> lang.substringBefore('-')
        }
    }

    private fun recognizer(lang: String?): TextRecognizer {
        val key = when (lang) {
            "ko", "zh" -> lang
            null, "ja" -> "ja"
            else -> "latin"
        }
        return recognizers.getOrPut(key) {
            when (key) {
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    private data class Piece(val rect: Rect, val text: String, val vertical: Boolean, val thickness: Int)

    /**
     * Reads [bitmap], groups the text into bubbles and translates each one. Text whose centre is
     * inside [skip] (already translated parts of the page) is left out.
     */
    suspend fun process(bitmap: Bitmap, skip: List<RectF>): List<Bubble> {
        val lang = pickLanguage()
        val text = ocrMutex.withLock {
            recognizer(lang).process(InputImage.fromBitmap(bitmap, 0)).await()
        }
        val pieces = pieces(text).filter { piece ->
            skip.none { it.contains(piece.rect.exactCenterX(), piece.rect.exactCenterY()) }
        }
        if (pieces.isEmpty()) return emptyList()
        val from = lang ?: autoLang ?: identify(pieces.joinToString("\n") { it.text }).also { autoLang = it }
        val groups = mergeIntoBubbles(pieces)
        val joiner = if (from == "ja" || from == "zh") "" else " "
        val bubbles = groups.mapNotNull { group ->
            val vertical = group.count { it.vertical } * 2 > group.size
            val ordered = if (vertical) {
                // Vertical columns read right to left
                group.sortedWith(compareByDescending<Piece> { it.rect.centerX() }.thenBy { it.rect.top })
            } else {
                group.sortedWith(compareBy<Piece> { it.rect.top / max(1, it.thickness) }.thenBy { it.rect.left })
            }
            val original = clean(ordered.joinToString(joiner) { it.text }, from)
            if (original.count { it.isLetter() } < 2) return@mapNotNull null
            val rect = RectF(ordered.first().rect)
            ordered.forEach { rect.union(RectF(it.rect)) }
            rect to (original to vertical)
        }
        if (bubbles.isEmpty()) return emptyList()
        val translated = translateAll(bubbles.map { it.second.first }, from)
        return bubbles.mapIndexed { i, (rect, info) ->
            Bubble(rect, info.first, translated[i], info.second)
        }
    }

    private fun pieces(text: Text): List<Piece> = text.textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val rect = line.boundingBox ?: return@mapNotNull null
            val content = line.text.trim()
            if (content.isEmpty()) return@mapNotNull null
            val vertical = rect.height() > rect.width() * 1.5f && content.length > 1
            Piece(rect, content, vertical, if (vertical) rect.width() else rect.height())
        }
    }

    /**
     * Lines close enough together belong to the same bubble. The reach is a share of the text
     * size, so big captions and small whispers both group properly and separate bubbles don't.
     */
    private fun mergeIntoBubbles(pieces: List<Piece>): List<List<Piece>> {
        val parent = IntArray(pieces.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        val grown = pieces.map { piece ->
            val reach = max(3, (piece.thickness * 0.45f).toInt())
            Rect(piece.rect).apply { inset(-reach, -reach) }
        }
        for (i in pieces.indices) {
            for (j in i + 1 until pieces.size) {
                // Very different text sizes are a caption next to a bubble, not one bubble
                val a = pieces[i].thickness.toFloat()
                val b = pieces[j].thickness.toFloat()
                if (max(a, b) > min(a, b) * 2.2f) continue
                if (Rect.intersects(grown[i], grown[j])) parent[find(i)] = find(j)
            }
        }
        return pieces.indices.groupBy(::find).values.map { ids -> ids.map { pieces[it] } }
    }

    private val spaces = Regex("""\s+""")
    private val brokenWord = Regex("""(\p{L})- (\p{L})""")

    private fun clean(text: String, lang: String): String {
        var out = text.replace(spaces, " ").trim()
        if (lang == "ja" || lang == "zh") out = out.replace(" ", "")
        // Lines split mid-word with a hyphen join back up
        return out.replace(brokenWord, "$1$2")
    }

    private suspend fun identify(sample: String): String {
        val client = LanguageIdentification.getClient()
        return try {
            client.identifyLanguage(sample).await().takeIf { it != "und" } ?: "ja"
        } catch (_: Exception) {
            "ja"
        } finally {
            client.close()
        }
    }

    private suspend fun translateAll(texts: List<String>, lang: String): List<String> {
        val source = TranslateLanguage.fromLanguageTag(lang) ?: error("Can't translate from \"$lang\" yet")
        val target = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH
        if (source == target) return texts
        val key = "$source>$target"
        val client = translator?.takeIf { it.first == key }?.second ?: run {
            translator?.second?.let { runCatching { it.close() } }
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
                .also { translator = key to it }
        }
        val models = RemoteModelManager.getInstance()
        val needed = listOf(source, target).filter { it != TranslateLanguage.ENGLISH }.any { language ->
            !models.isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()
        }
        if (needed) {
            mutableStatus.value = Status.Downloading
            val conditions = DownloadConditions.Builder().apply { if (!allowMobileData().get()) requireWifi() }.build()
            client.downloadModelIfNeeded(conditions).await()
            mutableStatus.value = Status.Idle
        }
        return coroutineScope {
            texts.map { text ->
                async {
                    val cacheKey = "$key:$text"
                    translationCache.get(cacheKey) ?: try {
                        client.translate(text).await().also { translationCache.put(cacheKey, it) }
                    } catch (e: Exception) {
                        xLogE("Translating a bubble failed", e)
                        text
                    }
                }
            }.awaitAll()
        }
    }

    // endregion
}
// KMK <--
