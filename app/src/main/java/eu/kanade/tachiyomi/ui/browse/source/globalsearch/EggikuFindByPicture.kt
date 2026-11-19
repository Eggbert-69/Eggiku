package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.search.EggikuSearchPrefs
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import exh.log.xLogE
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max

// KMK -->
/** Eggiku find by picture: asks SauceNAO which series a screenshot or panel is from */
object EggikuSauceNao {

    data class Match(
        val title: String,
        val part: String?,
        val similarity: Float,
        val thumbnail: String?,
        val site: String?,
    )

    class LimitException : Exception()

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_SIDE = 1400

    /** Shrinks the picture so the upload stays small; SauceNAO doesn't need full size */
    private fun jpeg(context: Context, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read the picture")
        val scale = MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { out ->
            sized.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }

    suspend fun search(context: Context, uri: Uri): List<Match> = withIOContext {
        val bytes = jpeg(context, uri)
        val url = "https://saucenao.com/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("output_type", "2")
            .addQueryParameter("numres", "12")
            .addQueryParameter("db", "999")
            .apply { EggikuSearchPrefs.sauceNaoKey().get().trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("api_key", it) } }
            .build()
            .toString()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "page.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val response = Injekt.get<NetworkHelper>().client.newCall(POST(url, body = body)).execute()
        if (response.code == 429) {
            response.close()
            throw LimitException()
        }
        val text = response.use {
            if (!it.isSuccessful) error("SauceNAO answered ${it.code}")
            it.body.string()
        }
        val root = json.parseToJsonElement(text).jsonObject
        val results = root["results"]?.jsonArray ?: return@withIOContext emptyList()
        results.mapNotNull { element ->
            val header = element.jsonObject["header"]?.jsonObject ?: return@mapNotNull null
            val data = element.jsonObject["data"] as? JsonObject ?: return@mapNotNull null
            fun field(name: String) = (data[name])?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            val title = field("source")?.takeIf { it.isNotBlank() && !it.startsWith("http") }
                ?: field("eng_name")
                ?: field("jp_name")
                ?: field("title")
                ?: return@mapNotNull null
            Match(
                title = title.trim(),
                part = field("part")?.trim()?.takeIf { it.isNotEmpty() },
                similarity = header["similarity"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
                thumbnail = header["thumbnail"]?.jsonPrimitive?.contentOrNull,
                site = header["index_name"]?.jsonPrimitive?.contentOrNull?.substringAfter(" - ")?.substringBefore(" -"),
            )
        }
            .filter { it.similarity >= 40f }
            .distinctBy { it.title.lowercase() }
    }
}

/** Pick (or share) a picture, see which series it's likely from, and search for it */
class EggikuFindByPictureScreen(private val sharedImage: String? = null) : Screen() {

    private sealed interface Status {
        data object Idle : Status
        data object Searching : Status
        data class Done(val matches: List<EggikuSauceNao.Match>) : Status
        data class Failed(val message: String?) : Status
        data object Limited : Status
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var picture by remember { mutableStateOf(sharedImage?.let(Uri::parse)) }
        var status by remember { mutableStateOf<Status>(Status.Idle) }
        val keyPref = remember { EggikuSearchPrefs.sauceNaoKey() }
        val key by keyPref.collectAsState()

        fun run(uri: Uri) {
            status = Status.Searching
            scope.launch {
                status = try {
                    Status.Done(EggikuSauceNao.search(context, uri))
                } catch (_: EggikuSauceNao.LimitException) {
                    Status.Limited
                } catch (e: Exception) {
                    xLogE("Find by picture failed", e)
                    Status.Failed(e.message)
                }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                picture = uri
                run(uri)
            }
        }
        LaunchedEffect(Unit) {
            picture?.let { if (status == Status.Idle) run(it) }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.search_by_picture),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "pick") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        picture?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier = Modifier
                                    .heightIn(max = 220.dp)
                                    .fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.ImageSearch, contentDescription = null)
                            Text(
                                stringResource(if (picture == null) KMR.strings.search_by_picture_pick else KMR.strings.search_by_picture_another),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(
                            stringResource(KMR.strings.search_by_picture_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (val s = status) {
                    Status.Idle -> Unit
                    Status.Searching -> item(key = "searching") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    }
                    Status.Limited -> item(key = "limited") { Text(stringResource(KMR.strings.search_by_picture_limited)) }
                    is Status.Failed -> item(key = "failed") { Text(stringResource(KMR.strings.search_by_picture_failed, s.message.orEmpty())) }
                    is Status.Done -> {
                        if (s.matches.isEmpty()) {
                            item(key = "none") { Text(stringResource(KMR.strings.search_by_picture_none)) }
                        }
                        items(s.matches, key = { "match-${it.title}" }) { match ->
                            Card(
                                onClick = { navigator.push(GlobalSearchScreen(match.title)) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = match.thumbnail, contentDescription = null, modifier = Modifier.width(64.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp),
                                    ) {
                                        Text(match.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        listOfNotNull(match.part, match.site).takeIf { it.isNotEmpty() }?.let {
                                            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            stringResource(
                                                KMR.strings.search_by_picture_match,
                                                String.format(Locale.getDefault(), "%.0f", match.similarity),
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "key") {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { keyPref.set(it.trim()) },
                        label = { Text(stringResource(KMR.strings.search_by_picture_key)) },
                        supportingText = { Text(stringResource(KMR.strings.search_by_picture_key_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
// KMK <--
