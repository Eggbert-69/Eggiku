package eu.kanade.tachiyomi.ui.reader.eggiku

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import exh.log.xLogE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.concurrent.TimeUnit

// KMK -->
/** One bubble to translate, with an id like "p3.2" (page 3, bubble 2) */
internal data class EggikuLine(val id: String, val page: Int, val text: String)

/** Language names for prompts ("ja" → "Japanese") */
private fun languageName(code: String): String = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

private fun systemPrompt(from: String, to: String) = """
    You are a professional manga, manhwa and manhua translator working for a well-loved scanlation team.
    Translate the speech bubbles from ${languageName(from)} into natural, fluent ${languageName(to)}.
    - Read the whole chapter first and use it as context: who is talking, what just happened, running jokes.
    - Make dialogue sound like real people talking, with the right tone for each character (rude, shy, formal, playful).
    - Keep character names, places and special terms consistent everywhere.
    - Keep honorifics like -san, -kun, -chan, senpai and sensei as they are.
    - Keep lines short enough to fit in a speech bubble; don't explain or add notes.
    - Sound effects: give a short English sound effect.
    - If a line is garbled text-recognition noise, translate your best guess or return it unchanged.
    - Return every id exactly once.
""".trimIndent()

private fun linesBlock(lines: List<EggikuLine>): String = buildString {
    var page = -1
    lines.forEach { line ->
        if (line.page != page) {
            page = line.page
            append("--- Page ").append(page + 1).append(" ---\n")
        }
        append('[').append(line.id).append("] ").append(line.text).append('\n')
    }
}

/**
 * Eggiku online translation with Gemini, using the reader's own free API key from Google AI
 * Studio. A whole chapter goes in one request (split only if it's very long), so the translation
 * knows the whole story of the chapter.
 */
object EggikuGemini {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun apiKey() = preferenceStore.getString("eggiku_gemini_key", "")

    /** Model id; Flash-Lite is Google's cheapest and is meant for translation */
    fun model() = preferenceStore.getString("eggiku_gemini_model", "gemini-3.1-flash-lite")

    /** Start on the next chapter while you read this one */
    fun translateAhead() = preferenceStore.getBoolean("eggiku_translate_ahead", true)

    const val KEY_URL = "https://aistudio.google.com/apikey"
    private const val FALLBACK_MODEL = "gemini-flash-lite-latest"
    private const val MAX_CHARS_PER_REQUEST = 14_000

    class QuotaException : Exception("Gemini's free limit is used up for now")

    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun hasKey() = apiKey().get().isNotBlank()

    /** Translates [lines] (a whole chapter, in reading order). Missing ids just aren't in the map */
    internal suspend fun translate(lines: List<EggikuLine>, from: String, to: String, title: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Long chapters are split by page; each part gets the end of the previous part as context
        val chunks = mutableListOf<MutableList<EggikuLine>>(mutableListOf())
        var size = 0
        lines.groupBy { it.page }.values.forEach { pageLines ->
            val pageSize = pageLines.sumOf { it.text.length + 12 }
            if (size + pageSize > MAX_CHARS_PER_REQUEST && chunks.last().isNotEmpty()) {
                chunks += mutableListOf<EggikuLine>()
                size = 0
            }
            chunks.last() += pageLines
            size += pageSize
        }
        var previous = emptyList<Pair<String, String>>()
        chunks.filter { it.isNotEmpty() }.forEach { chunk ->
            val prompt = buildString {
                title?.let { append("Series: ").append(it).append('\n') }
                if (previous.isNotEmpty()) {
                    append("Earlier in this chapter (already translated, for context only):\n")
                    previous.forEach { (original, translated) -> append("- ").append(original).append(" => ").append(translated).append('\n') }
                    append('\n')
                }
                append("Translate these lines. Answer with a JSON array of {\"id\", \"text\"}.\n\n")
                append(linesBlock(chunk))
            }
            val translated = request(prompt, systemPrompt(from, to))
            result += translated
            previous = chunk.takeLast(12).mapNotNull { line -> translated[line.id]?.let { line.text to it } }
        }
        return result
    }

    /** Sends one small request; returns null if it worked, or what went wrong */
    suspend fun test(): String? = try {
        val out = request(
            "Translate these lines. Answer with a JSON array of {\"id\", \"text\"}.\n\n[t1] おはよう！\n",
            systemPrompt("ja", "en"),
        )
        if (out["t1"].isNullOrBlank()) "Gemini answered, but without a translation" else null
    } catch (e: Exception) {
        e.message ?: e::class.simpleName
    }

    private suspend fun request(prompt: String, system: String): Map<String, String> = withIOContext {
        val key = apiKey().get().trim()
        if (key.isEmpty()) error("No Gemini key yet")
        var model = model().get().trim().ifEmpty { FALLBACK_MODEL }
        var withThinkingOff = true
        repeat(3) {
            val body = body(prompt, system, withThinkingOff).toString().toRequestBody("application/json".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            client.newCall(POST(url, headers = Headers.headersOf("x-goog-api-key", key), body = body)).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> return@withIOContext parse(text)
                    response.code == 429 -> throw QuotaException()
                    // Model renamed or retired: fall back to the always-current alias
                    response.code == 404 && model != FALLBACK_MODEL -> model = FALLBACK_MODEL
                    // Some models don't take the thinking setting; try once without it
                    response.code == 400 && withThinkingOff && "thinking" in text.lowercase() -> withThinkingOff = false
                    response.code == 400 && "API key" in text -> error("Gemini didn't accept the key")
                    else -> error("Gemini answered ${response.code}")
                }
            }
        }
        error("Gemini didn't answer")
    }

    private fun body(prompt: String, system: String, thinkingOff: Boolean) = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", system) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", prompt) } }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", 0.4)
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "STRING") }
                        putJsonObject("text") { put("type", "STRING") }
                    }
                    putJsonArray("required") {
                        add("id")
                        add("text")
                    }
                }
            }
            // Translation doesn't need thinking time, and skipping it is much faster
            if (thinkingOff) putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
        }
    }

    private fun parse(text: String): Map<String, String> {
        val root = json.parseToJsonElement(text).jsonObject
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return emptyMap()
        val answer = parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        val items = runCatching { json.parseToJsonElement(answer) as? JsonArray }.getOrNull() ?: return emptyMap()
        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val translated = obj["text"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            id.trim('[', ']', ' ') to translated
        }.toMap()
    }
}

/**
 * Eggiku on-device translation with Gemini Nano (on phones that have it, like the Galaxy S25 /
 * S26 and Z Fold7 / Fold8). Offline and private; used when online Gemini isn't available.
 * Nano can only take a few thousand words at a time, so the chapter goes in small parts.
 */
object EggikuNano {

    private const val LINES_PER_PART = 22
    private val answerLine = Regex("""^\s*\[([^\]]+)]\s*(.+)$""")
    private var available: Boolean? = null

    suspend fun isAvailable(): Boolean {
        available?.let { return it }
        val ok = try {
            Generation.getClient().checkStatus() == FeatureStatus.AVAILABLE
        } catch (e: Exception) {
            xLogE("Gemini Nano check failed", e)
            false
        }
        available = ok
        return ok
    }

    internal suspend fun translate(lines: List<EggikuLine>, from: String, to: String): Map<String, String> {
        val model = Generation.getClient()
        val result = mutableMapOf<String, String>()
        lines.chunked(LINES_PER_PART).forEach { part ->
            val prompt = buildString {
                append(systemPrompt(from, to)).append("\n\n")
                append("Answer with one line per id, exactly like: [id] translation\n\n")
                append(linesBlock(part))
            }
            val response = model.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.3f
                    topK = 16
                },
            )
            val answer = response.candidates.firstOrNull()?.text.orEmpty()
            answer.lineSequence().forEach { line ->
                answerLine.find(line)?.let { match -> result[match.groupValues[1].trim()] = match.groupValues[2].trim() }
            }
        }
        return result
    }
}
// KMK <--
