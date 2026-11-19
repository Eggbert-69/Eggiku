package eu.kanade.tachiyomi.ui.reader.eggiku

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import exh.log.xLogE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import kotlin.coroutines.resume

// KMK -->
/**
 * Eggiku raw translation: reads the text on screen and lays a translation over each speech bubble.
 * Everything runs on the phone with Google's ML Kit: text recognition comes through Play services
 * and each language pack (about 30 MB) downloads the first time it's needed.
 */
object EggikuTranslate {

    data class Block(
        /** Where the text is, in window pixels */
        val rect: Rect,
        val original: String,
        val translated: String,
    )

    sealed interface State {
        data object Idle : State
        data class Working(val downloading: Boolean) : State
        data class Done(val blocks: List<Block>) : State
        data class Failed(val message: String?) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** "auto", or a language code like "ja", "ko", "zh" */
    fun rawLanguage() = preferenceStore.getString("eggiku_translate_from", "auto")

    /** Download language packs on mobile data too */
    fun allowMobileData() = preferenceStore.getBoolean("eggiku_translate_mobile_data", true)

    private var job: Job? = null

    fun close() {
        job?.cancel()
        mutableState.value = State.Idle
    }

    /**
     * Translates what's on screen. [sourceLang] is the source's language (used when the raw
     * language is set to auto); the translation goes to the phone's language.
     */
    fun run(activity: Activity, view: View, sourceLang: String?, scope: CoroutineScope) {
        if (state.value is State.Working) return
        job?.cancel()
        mutableState.value = State.Working(downloading = false)
        job = scope.launch {
            mutableState.value = try {
                val (bitmap, offsetX, offsetY) = capture(activity, view) ?: error("Couldn't read the screen")
                val from = pickLanguage(sourceLang)
                val blocks = recognize(bitmap, from)
                bitmap.recycle()
                if (blocks.isEmpty()) {
                    State.Done(emptyList())
                } else {
                    val lang = from ?: identify(blocks.joinToString("\n") { it.second })
                    State.Done(translate(blocks, lang, offsetX, offsetY))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                xLogE("Eggiku translate failed", e)
                State.Failed(e.message)
            }
        }
    }

    private fun pickLanguage(sourceLang: String?): String? {
        val chosen = rawLanguage().get()
        val lang = if (chosen == "auto") sourceLang else chosen
        return when {
            lang == null -> null
            lang.startsWith("ja") -> "ja"
            lang.startsWith("ko") -> "ko"
            lang.startsWith("zh") -> "zh"
            lang == "all" || lang == "other" -> null
            else -> lang.substringBefore('-')
        }
    }

    private data class Capture(val bitmap: Bitmap, val offsetX: Int, val offsetY: Int)

    private suspend fun capture(activity: Activity, view: View): Capture? {
        if (view.width == 0 || view.height == 0) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val ok = suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(
                    activity.window,
                    Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                    bitmap,
                    { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
        return if (ok) Capture(bitmap, location[0], location[1]) else null
    }

    private fun recognizer(lang: String?): TextRecognizer = when (lang) {
        "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        null, "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val cjk = setOf("ja", "ko", "zh")

    /** Text blocks (speech bubbles, captions) with where they are on the capture */
    private suspend fun recognize(bitmap: Bitmap, lang: String?): List<Pair<Rect, String>> {
        val client = recognizer(lang)
        try {
            val text = client.process(InputImage.fromBitmap(bitmap, 0)).await()
            // Lines of one bubble belong together; CJK lines join without spaces
            val joiner = if (lang == null || lang in cjk) "" else " "
            return text.textBlocks.mapNotNull { block ->
                val rect = block.boundingBox ?: return@mapNotNull null
                val content = block.lines.joinToString(joiner) { it.text.trim() }.trim()
                if (content.length < 2 || content.all { !it.isLetter() }) null else rect to content
            }
        } finally {
            client.close()
        }
    }

    private suspend fun identify(text: String): String {
        val client = LanguageIdentification.getClient()
        return try {
            client.identifyLanguage(text).await().takeIf { it != "und" } ?: "ja"
        } catch (_: Exception) {
            "ja"
        } finally {
            client.close()
        }
    }

    private suspend fun translate(blocks: List<Pair<Rect, String>>, lang: String, offsetX: Int, offsetY: Int): List<Block> {
        val source = TranslateLanguage.fromLanguageTag(lang) ?: error("Can't translate from \"$lang\" yet")
        val target = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH
        if (source == target) {
            return blocks.map { (rect, text) -> Block(Rect(rect).apply { offset(offsetX, offsetY) }, text, text) }
        }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        try {
            val conditions = DownloadConditions.Builder().apply { if (!allowMobileData().get()) requireWifi() }.build()
            val models = RemoteModelManager.getInstance()
            val needed = listOf(source, target).filter { it != TranslateLanguage.ENGLISH }.any { language ->
                !models.isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()
            }
            if (needed) mutableState.value = State.Working(downloading = true)
            translator.downloadModelIfNeeded(conditions).await()
            mutableState.value = State.Working(downloading = false)
            return blocks.map { (rect, text) ->
                Block(
                    rect = Rect(rect).apply { offset(offsetX, offsetY) },
                    original = text,
                    translated = translator.translate(text).await(),
                )
            }
        } finally {
            translator.close()
        }
    }
}
// KMK <--
