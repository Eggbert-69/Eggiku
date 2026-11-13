package eu.kanade.tachiyomi.ui.stats

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.EggikuStatsFormat
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.achievements.EggikuAchievements
import eu.kanade.tachiyomi.data.rating.EggikuRatings
import eu.kanade.tachiyomi.data.stats.EggikuGenres
import eu.kanade.tachiyomi.data.stats.EggikuReadingLog
import eu.kanade.tachiyomi.ui.library.EggikuCategoryLock
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.components.material.IconButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import tachiyomi.presentation.core.util.collectAsState as collectPrefAsState

// KMK -->

/**
 * Eggiku: swipeable weekly, monthly and yearly reading recaps that can be shared as images.
 * [startMonthOffset] opens a past month straight away, e.g. -1 for last month from the monthly wrap.
 */
class EggikuRecapScreen(private val startMonthOffset: Int = 0) : Screen() {

    private enum class Period { WEEK, MONTH, YEAR }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var period by rememberSaveable { mutableStateOf(Period.MONTH) }
        var offset by rememberSaveable { mutableIntStateOf(startMonthOffset) }

        val version by EggikuReadingLog.changes.collectAsState()
        val log = remember(version) { EggikuReadingLog.all() }
        val today = remember(version) { EggikuReadingLog.today() }

        val (from, to) = remember(period, offset, today) { range(period, offset, today) }
        val total = remember(log, from, to) { EggikuReadingLog.sum(log, from, to) }
        val newSeries = remember(log, from, to) { EggikuReadingLog.newSeries(log, from, to).size }
        val streak = remember(log, today) { EggikuReadingLog.streak(log, today) }
        val busiestDay = remember(log, from, to) {
            log.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
                .entries.groupBy({ it.key.dayOfWeek }, { it.value.seconds })
                .mapValues { it.value.sum() }
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0 }
                ?.key
        }
        val topHour = total.hours.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

        // Titles for the cards; series in locked categories never appear, even if locked later
        val titles by produceState(emptyMap<Long, String>(), total.series, total.seriesZooms) {
            val ids = (total.series.keys + total.seriesZooms.keys)
            value = withIOContext {
                ids.mapNotNull { id ->
                    val categories = Injekt.get<GetCategories>().await(id).map { it.id }
                    if (EggikuCategoryLock.isPrivate(categories)) return@mapNotNull null
                    val title = Injekt.get<GetManga>().await(id)?.title ?: return@mapNotNull null
                    id to title
                }.toMap()
            }
        }

        val periodLabel = label(period, from, to)
        val ratingEntries by EggikuRatings.pref().collectPrefAsState()
        val ratings = remember(ratingEntries) { EggikuRatings.parse(ratingEntries) }
        val topGenre by produceState<String?>(null, from, to) {
            value = withIOContext {
                try {
                    val zone = java.time.ZoneId.systemDefault()
                    EggikuGenres.shares(
                        from = from.atStartOfDay(zone).toInstant().toEpochMilli(),
                        to = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
                        limit = 1,
                    ).firstOrNull()?.genre
                } catch (_: Exception) {
                    null
                }
            }
        }
        val badgesEarned = remember(from, to, version) {
            EggikuAchievements.earned().values.count { !it.isBefore(from) && !it.isAfter(to) }
        }
        val pages = buildList<@Composable () -> Unit> {
            // Wrapped: everything on one card, made for sharing
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_wrapped_title)) {
                    BigNumber(total.chapters.toString(), stringResource(KMR.strings.stats_chapters))
                    val (h, m) = EggikuStatsFormat.hoursAndMinutes(total.seconds)
                    Line("⏱️ " + stringResource(KMR.strings.recap_wrapped_time, stringResource(KMR.strings.stats_duration, h, m)))
                    total.series.entries.filter { it.key in titles }.maxByOrNull { it.value }?.let { most ->
                        Line("📚 " + stringResource(KMR.strings.recap_wrapped_most_read, titles[most.key].orEmpty()))
                    }
                    total.series.keys.filter { it in titles && (ratings[it] ?: 0) > 0 }
                        .maxWithOrNull(compareBy<Long> { ratings[it] ?: 0 }.thenBy { total.series[it] ?: 0 })
                        ?.let { fav ->
                            Line("⭐ " + stringResource(KMR.strings.recap_wrapped_favourite, titles[fav].orEmpty(), "★".repeat(ratings[fav] ?: 0)))
                        }
                    topGenre?.let { Line("🏷️ " + stringResource(KMR.strings.recap_wrapped_genre, it)) }
                    Line("🔥 " + stringResource(KMR.strings.recap_longest_streak, streak.longest))
                    Line("👍 " + stringResource(KMR.strings.recap_wrapped_swiped, EggikuStatsFormat.distance(total.swipeMm)))
                    if (newSeries > 0) Line("🆕 " + pluralStringResource(KMR.plurals.recap_new_series, newSeries, newSeries))
                    if (badgesEarned > 0) Line("🏅 " + stringResource(KMR.strings.recap_wrapped_badges, badgesEarned))
                }
            }
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_total_title)) {
                    BigNumber(total.chapters.toString(), stringResource(KMR.strings.stats_chapters))
                    val (h, m) = EggikuStatsFormat.hoursAndMinutes(total.seconds)
                    BigNumber(stringResource(KMR.strings.stats_duration, h, m), stringResource(KMR.strings.recap_spent_reading))
                }
            }
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_top_series)) {
                    val top = total.series.entries.filter { it.key in titles }.sortedByDescending { it.value }.take(3)
                    if (top.isEmpty()) {
                        Line(stringResource(KMR.strings.recap_nothing_yet))
                    } else {
                        top.forEachIndexed { index, entry ->
                            Line("${index + 1}. ${titles[entry.key].orEmpty()}")
                            Line(pluralStringResource(KMR.plurals.recap_chapters, entry.value, entry.value), small = true)
                        }
                    }
                    if (newSeries > 0) {
                        Spacer(Modifier.height(12.dp))
                        Line(pluralStringResource(KMR.plurals.recap_new_series, newSeries, newSeries))
                    }
                }
            }
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_when_title)) {
                    if (topHour != null) {
                        BigNumber(EggikuStatsFormat.hour(topHour), stringResource(KMR.strings.recap_reader_type))
                    }
                    if (busiestDay != null) {
                        Line(
                            stringResource(
                                KMR.strings.recap_busiest_day,
                                busiestDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            ),
                        )
                    }
                    if (total.longestSession > 0) {
                        val (h, m) = EggikuStatsFormat.hoursAndMinutes(total.longestSession)
                        Line(stringResource(KMR.strings.recap_longest_session, stringResource(KMR.strings.stats_duration, h, m)))
                    }
                    if (topHour == null && busiestDay == null) Line(stringResource(KMR.strings.recap_nothing_yet))
                }
            }
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_distance_title)) {
                    BigNumber(EggikuStatsFormat.distance(total.swipeMm), stringResource(KMR.strings.recap_swiped))
                    EggikuStatsFormat.landmark(total.swipeMm)?.let {
                        Line(stringResource(KMR.strings.recap_landmark, stringResource(it)))
                    }
                    Line(stringResource(KMR.strings.recap_zooms, total.zooms, EggikuStatsFormat.distance(total.panMm)))
                    total.seriesZooms.filterKeys { it in titles }.maxByOrNull { it.value }?.let { most ->
                        titles[most.key]?.takeIf { it.isNotBlank() }?.let { Line(stringResource(KMR.strings.recap_most_zoomed, it)) }
                    }
                }
            }
            add {
                RecapCard(periodLabel, stringResource(KMR.strings.recap_streak_title)) {
                    BigNumber("🔥 ${streak.current}", stringResource(KMR.strings.stats_streak))
                    Line(stringResource(KMR.strings.recap_longest_streak, streak.longest))
                }
            }
        }
        val pagerState = rememberPagerState { pages.size }
        val layers = List(pages.size) { rememberGraphicsLayer() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.stats_recaps),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                val layer = layers.getOrNull(pagerState.currentPage) ?: return@IconButton
                                scope.launch {
                                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                    shareImage(context, bitmap)
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(KMR.strings.recap_share))
                        }
                        IconButton(
                            onClick = {
                                val layer = layers.getOrNull(pagerState.currentPage) ?: return@IconButton
                                scope.launch {
                                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                    val saved = withIOContext { saveImage(context, bitmap) }
                                    context.toast(
                                        if (saved) KMR.strings.recap_saved else KMR.strings.recap_save_failed,
                                    )
                                    if (!saved) shareImage(context, bitmap)
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(KMR.strings.recap_save))
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Period.entries.forEach { p ->
                        FilterChip(
                            selected = p == period,
                            onClick = {
                                period = p
                                offset = 0
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (p) {
                                            Period.WEEK -> KMR.strings.recap_week
                                            Period.MONTH -> KMR.strings.recap_month
                                            Period.YEAR -> KMR.strings.recap_year
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { offset-- }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(KMR.strings.recap_previous))
                    }
                    IconButton(onClick = { offset++ }, enabled = offset < 0) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(KMR.strings.recap_next))
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                    pageSpacing = 12.dp,
                ) { page ->
                    val layer = layers[page]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .drawWithContent {
                                // Record the card so it can be shared as an image
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            },
                    ) {
                        pages[page]()
                    }
                }
            }
        }
    }

    private fun range(period: Period, offset: Int, today: LocalDate): Pair<LocalDate, LocalDate> {
        return when (period) {
            Period.WEEK -> {
                val start = EggikuReadingLog.weekStart(today).plusWeeks(offset.toLong())
                start to start.plusDays(6)
            }
            Period.MONTH -> {
                val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
                start to start.with(TemporalAdjusters.lastDayOfMonth())
            }
            Period.YEAR -> {
                val start = today.withDayOfYear(1).plusYears(offset.toLong())
                start to start.with(TemporalAdjusters.lastDayOfYear())
            }
        }
    }

    private fun label(period: Period, from: LocalDate, to: LocalDate): String {
        val locale = Locale.getDefault()
        return when (period) {
            Period.WEEK -> {
                val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                "${from.format(f)} – ${to.format(f)}"
            }
            Period.MONTH -> from.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            Period.YEAR -> from.year.toString()
        }
    }

    /** Saves the card to Pictures/Eggiku so it shows up in the gallery */
    private fun saveImage(context: Context, bitmap: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "eggiku-wrapped-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Eggiku")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun shareImage(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "eggiku_recaps").apply { mkdirs() }
        val file = File(dir, "eggiku-recap.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = file.getUriCompat(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

@Composable
private fun RecapCard(period: String, title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(period, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.weight(1f))
        Text(
            "Eggiku",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun BigNumber(value: String, label: String) {
    Column {
        Text(
            value,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Line(text: String, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.Start,
    )
}
// KMK <--
