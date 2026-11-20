package eu.kanade.tachiyomi.ui.reader.eggiku

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.LruCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import kotlin.math.min

// KMK -->
/**
 * Eggiku whole-chapter translation. When a chapter opens with translation on, every page is
 * loaded and read straight from its image, then the whole chapter's text goes to Gemini in one
 * request (falling back to Gemini Nano on the phone, then the quick offline translator).
 * Finished chapters are saved, and the next chapter can be done while you read.
 */
object EggikuChapterTranslator {

    @Serializable
    data class SavedBubble(
        val l: Float,
        val t: Float,
        val r: Float,
        val b: Float,
        val original: String,
        val translated: String,
        val vertical: Boolean,
    ) {
        fun toBubble() = EggikuTranslate.Bubble(RectF(l, t, r, b), original, translated, vertical)
    }

    @Serializable
    data class SavedPage(val width: Int, val height: Int, val bubbles: List<SavedBubble>)

    @Serializable
    data class SavedChapter(
        val chapterId: Long,
        /** "gemini", "nano" or "offline": which engine did it */
        val engine: String,
        val pages: Map<Int, SavedPage>,
    )

    enum class Stage { Loading, Reading, Translating }

    sealed interface Progress {
        val chapterId: Long

        data class Working(override val chapterId: Long, val stage: Stage, val done: Int, val total: Int, val engine: String?) : Progress
        data class Done(override val chapterId: Long, val engine: String) : Progress
        data class Failed(override val chapterId: Long, val message: String?) : Progress
    }

    private val mutableProgress = MutableStateFlow<Progress?>(null)

    /** The chapter being read right now: what the egg screen shows */
    val progress: StateFlow<Progress?> = mutableProgress.asStateFlow()

    /** Chapters where you tapped Skip */
    private val mutableSkipped = MutableStateFlow<Set<Long>>(emptySet())
    val skipped: StateFlow<Set<Long>> = mutableSkipped.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val memory = LruCache<Long, SavedChapter>(12)
    private val jobs = mutableMapOf<Long, Job>()
    private var currentChapterId: Long? = null
    private var aheadWatcher: Job? = null
    var seriesTitle: String? = null

    private const val TILE_RATIO = 2.2f
    private const val MAX_DECODE_WIDTH = 1800

    fun skip(chapterId: Long) {
        mutableSkipped.value = mutableSkipped.value + chapterId
        EggikuTranslate.refreshAll()
    }

    /** The saved translation for a page, if its image is the same size it was read at */
    fun pageResult(chapterId: Long, pageIndex: Int, width: Int, height: Int): List<EggikuTranslate.Bubble>? {
        val chapter = memory.get(chapterId) ?: return null
        val page = chapter.pages[pageIndex] ?: return null
        if (page.width != width || page.height != height) return null
        return page.bubbles.map { it.toBubble() }
    }

    /** Called whenever the reader's chapters change, and when translation is switched on */
    fun onChapters(context: Context, chapters: ViewerChapters?) {
        val current = chapters?.currChapter ?: return
        val id = current.chapter.id ?: return
        currentChapterId = id
        if (!EggikuTranslate.active.value) {
            mutableProgress.value = null
            return
        }
        val app = context.applicationContext
        start(app, current, foreground = true)
        // Translate ahead: once this chapter is done and the next one has loaded, do that too
        aheadWatcher?.cancel()
        val next = chapters.nextChapter
        if (next != null && EggikuGemini.translateAhead().get()) {
            aheadWatcher = EggikuTranslate.scope.launch {
                jobs[id]?.join()
                next.stateFlow.first { it is ReaderChapter.State.Loaded }
                start(app, next, foreground = false)
            }
        }
    }

    fun cancelAll() {
        aheadWatcher?.cancel()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        mutableProgress.value = null
        currentChapterId = null
    }

    private fun report(progress: Progress) {
        if (progress.chapterId == currentChapterId) mutableProgress.value = progress
    }

    private fun start(context: Context, chapter: ReaderChapter, foreground: Boolean) {
        val id = chapter.chapter.id ?: return
        if (jobs[id]?.isActive == true) {
            if (foreground) report(mutableProgress.value?.takeIf { it.chapterId == id } ?: Progress.Working(id, Stage.Loading, 0, 0, null))
            return
        }
        val saved = memory.get(id) ?: load(context, id)?.also { memory.put(id, it) }
        if (saved != null) {
            report(Progress.Done(id, saved.engine))
            return
        }
        jobs[id] = EggikuTranslate.scope.launch {
            try {
                val result = translateChapter(context, chapter, id)
                memory.put(id, result)
                save(context, result)
                report(Progress.Done(id, result.engine))
                EggikuTranslate.refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                xLogE("Chapter translation failed", e)
                report(Progress.Failed(id, e.message))
            } finally {
                jobs.remove(id)
            }
        }
    }

    private suspend fun translateChapter(context: Context, chapter: ReaderChapter, id: Long): SavedChapter {
        report(Progress.Working(id, Stage.Loading, 0, 0, null))
        chapter.stateFlow.first { it is ReaderChapter.State.Loaded || it is ReaderChapter.State.Error }
        val pages = chapter.pages ?: error("The chapter didn't load")
        val total = pages.size

        // 1. Load and read every page, straight from the images
        data class PageText(val index: Int, val width: Int, val height: Int, val raws: List<EggikuTranslate.Raw>)
        val read = mutableListOf<PageText>()
        var from: String? = null
        pages.forEachIndexed { i, page ->
            report(Progress.Working(id, Stage.Reading, i, total, null))
            val image = loadImage(chapter, page) ?: return@forEachIndexed
            try {
                val (width, height, pieces) = image
                val lang = from ?: EggikuTranslate.resolveLanguage { pieces.joinToString("\n") { it.text } }.also { from = it }
                read += PageText(page.index, width, height, EggikuTranslate.group(pieces, lang))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                xLogE("Couldn't read page ${page.index}", e)
            }
        }
        val lang = from ?: "ja"
        val to = EggikuTranslate.targetLanguage()
        val lines = read.flatMap { page ->
            page.raws.mapIndexed { b, raw -> EggikuLine("p${page.index}.$b", page.index, raw.original) }
        }

        // 2. Translate the whole chapter at once
        var engine = "offline"
        val translated = mutableMapOf<String, String>()
        if (lines.isNotEmpty()) {
            if (EggikuGemini.hasKey()) {
                report(Progress.Working(id, Stage.Translating, total, total, "gemini"))
                try {
                    translated += EggikuGemini.translate(lines, lang, to, seriesTitle)
                    if (translated.isNotEmpty()) engine = "gemini"
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    xLogE("Gemini translation failed, trying on the phone", e)
                }
            }
            val missing = lines.filter { it.id !in translated }
            if (missing.isNotEmpty() && EggikuNano.isAvailable()) {
                report(Progress.Working(id, Stage.Translating, total, total, "nano"))
                try {
                    translated += EggikuNano.translate(missing, lang, to)
                    if (engine == "offline" && translated.isNotEmpty()) engine = "nano"
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    xLogE("Gemini Nano translation failed", e)
                }
            }
            val stillMissing = lines.filter { it.id !in translated }
            if (stillMissing.isNotEmpty()) {
                report(Progress.Working(id, Stage.Translating, total, total, "offline"))
                val quick = EggikuTranslate.translateOffline(stillMissing.map { it.text }, lang)
                stillMissing.forEachIndexed { i, line -> translated[line.id] = quick[i] }
            }
        }

        val saved = read.associate { page ->
            page.index to SavedPage(
                page.width,
                page.height,
                page.raws.mapIndexed { b, raw ->
                    SavedBubble(
                        raw.rect.left,
                        raw.rect.top,
                        raw.rect.right,
                        raw.rect.bottom,
                        raw.original,
                        translated["p${page.index}.$b"] ?: raw.original,
                        raw.vertical,
                    )
                },
            )
        }
        return SavedChapter(id, engine, saved)
    }

    /**
     * Makes sure the page's image is downloaded, then reads its text. Tall webtoon strips are read
     * in overlapping slices so the text recognizer sees them at a sensible size.
     */
    private suspend fun loadImage(chapter: ReaderChapter, page: ReaderPage): Triple<Int, Int, List<EggikuTranslate.Piece>>? {
        if (page.status != Page.State.Ready || page.stream == null) {
            val loader = chapter.pageLoader ?: return null
            val loading = EggikuTranslate.scope.launch(Dispatchers.IO) { runCatching { loader.loadPage(page) } }
            val state = withTimeoutOrNull(90_000) {
                page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
            }
            loading.cancel()
            if (state != Page.State.Ready) return null
        }
        val stream = page.stream ?: return null
        val bytes = withContext(Dispatchers.IO) { stream().use { it.readBytes() } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (width / sample > MAX_DECODE_WIDTH) sample *= 2

        val tileHeight = min(height, (width * TILE_RATIO).toInt())
        val overlap = (tileHeight * 0.15f).toInt()
        val pieces = mutableListOf<EggikuTranslate.Piece>()
        val decoder = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
                }
            }.getOrNull()
        }
        try {
            var top = 0
            while (top < height) {
                val bottom = min(height, top + tileHeight)
                val region = Rect(0, top, width, bottom)
                val bitmap: Bitmap = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    decoder?.decodeRegion(region, options)
                        ?: if (top == 0 && bottom == height) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) else null
                } ?: break
                val found = try {
                    EggikuTranslate.readPieces(bitmap)
                } finally {
                    bitmap.recycle()
                }
                found.forEach { piece ->
                    val r = Rect(piece.rect.left * sample, piece.rect.top * sample + top, piece.rect.right * sample, piece.rect.bottom * sample + top)
                    // Lines cut by a slice edge are picked up whole by the next slice
                    val cutByEdge = (top > 0 && r.top <= top + 2) || (bottom < height && r.bottom >= bottom - 2)
                    val duplicate = pieces.any { other -> overlapRatio(other.rect, r) > 0.5f }
                    if (!cutByEdge && !duplicate) pieces += piece.copy(rect = r, thickness = piece.thickness * sample)
                }
                if (bottom >= height) break
                top = bottom - overlap
            }
        } finally {
            decoder?.recycle()
        }
        return Triple(width, height, pieces)
    }

    private fun overlapRatio(a: Rect, b: Rect): Float {
        val i = Rect()
        if (!i.setIntersect(a, b)) return 0f
        val area = (i.width() * i.height()).toFloat()
        return area / max(1, min(a.width() * a.height(), b.width() * b.height()))
    }

    // Saving

    private fun dir(context: Context) = File(context.cacheDir, "eggiku_translations").apply { mkdirs() }

    private fun load(context: Context, id: Long): SavedChapter? = try {
        File(dir(context), "$id.json").takeIf { it.exists() }?.readText()?.let { json.decodeFromString<SavedChapter>(it) }
    } catch (_: Exception) {
        null
    }

    private fun save(context: Context, chapter: SavedChapter) {
        runCatching {
            val folder = dir(context)
            File(folder, "${chapter.chapterId}.json").writeText(json.encodeToString(SavedChapter.serializer(), chapter))
            // Keep the 300 newest chapters
            folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(300)?.forEach { it.delete() }
        }
    }

    /** For settings: wipe saved translations */
    fun clearSaved(context: Context) {
        memory.evictAll()
        runCatching { dir(context).deleteRecursively() }
    }
}
// KMK <--
