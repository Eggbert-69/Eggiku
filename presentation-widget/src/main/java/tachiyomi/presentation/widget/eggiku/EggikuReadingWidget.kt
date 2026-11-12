package tachiyomi.presentation.widget.eggiku

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import tachiyomi.core.common.Constants
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.components.CoverHeight
import tachiyomi.presentation.widget.components.CoverWidth
import tachiyomi.presentation.widget.components.LockedWidget
import tachiyomi.presentation.widget.components.UpdatesMangaCover
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku home screen widget: your streak, this week's goal, and the top of Continue reading.
 * The app keeps the numbers up to date in [EggikuWidgetData]; the widget only draws them.
 */
object EggikuWidgetData {
    private val store: PreferenceStore by lazy { Injekt.get() }

    fun streak() = store.getInt(Preference.appStateKey("eggiku_widget_streak"), 0)
    fun readToday() = store.getBoolean(Preference.appStateKey("eggiku_widget_read_today"), false)
    fun weekChapters() = store.getInt(Preference.appStateKey("eggiku_widget_week_chapters"), 0)
    fun weekGoal() = store.getInt(Preference.appStateKey("eggiku_widget_week_goal"), 0)

    /** Manga ids, comma-separated, in Continue reading order */
    fun continueIds() = store.getString(Preference.appStateKey("eggiku_widget_continue"), "")
}

class EggikuReadingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @SuppressLint("RestrictedApi")
    private val foreground = ColorProvider(R.color.appwidget_on_secondary_container)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = Injekt.get<SecurityPreferences>().useAuthenticator().get()
        val streak = EggikuWidgetData.streak().get()
        val readToday = EggikuWidgetData.readToday().get()
        val week = EggikuWidgetData.weekChapters().get()
        val goal = EggikuWidgetData.weekGoal().get()
        val covers = if (locked) emptyList() else loadCovers(context)

        val container = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.appwidget_background))
            .appWidgetBackground()
            .appWidgetBackgroundRadius()
            .padding(12.dp)

        provideContent {
            if (locked) {
                LockedWidget(foreground = foreground, modifier = container)
                return@provideContent
            }
            Content(container, streak, readToday, week, goal, covers)
        }
    }

    @Composable
    private fun Content(
        modifier: GlanceModifier,
        streak: Int,
        readToday: Boolean,
        week: Int,
        goal: Int,
        covers: List<Pair<Long, Bitmap?>>,
    ) {
        val context = LocalContext.current
        val openApp = Intent(context, Class.forName(Constants.MAIN_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Column(modifier = modifier.clickable(actionStartActivity(openApp))) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔥 $streak",
                    style = TextStyle(color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (readToday) KMR.strings.widget_streak_safe else KMR.strings.widget_streak_read_today,
                    ),
                    style = TextStyle(color = foreground, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (goal > 0) {
                Text(
                    text = stringResource(KMR.strings.stats_goal_progress, week, goal),
                    style = TextStyle(color = foreground, fontSize = 12.sp),
                )
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = (week / goal.toFloat()).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = foreground,
                    backgroundColor = ColorProvider(R.color.appwidget_secondary_container),
                )
            } else {
                Text(
                    text = stringResource(KMR.strings.widget_week_chapters, week),
                    style = TextStyle(color = foreground, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            if (covers.isEmpty()) {
                Text(
                    text = stringResource(KMR.strings.widget_nothing_to_continue),
                    style = TextStyle(color = foreground, fontSize = 12.sp),
                )
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    covers.forEach { (mangaId, bitmap) ->
                        val intent = Intent(context, Class.forName(Constants.MAIN_ACTIVITY)).apply {
                            action = Constants.SHORTCUT_MANGA
                            putExtra(Constants.MANGA_EXTRA, mangaId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addCategory(mangaId.toString())
                        }
                        Box(modifier = GlanceModifier.padding(end = 6.dp)) {
                            UpdatesMangaCover(
                                cover = bitmap,
                                modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun loadCovers(context: Context): List<Pair<Long, Bitmap?>> = withIOContext {
        val ids = EggikuWidgetData.continueIds().get().split(',').mapNotNull { it.toLongOrNull() }.take(4)
        // The repository directly: GetManga drags in the extension API, which widgets don't have
        val mangaRepository = Injekt.get<MangaRepository>()
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        ids.mapNotNull { id ->
            val manga = try {
                mangaRepository.getMangaById(id)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val request = ImageRequest.Builder(context)
                .data(manga.asMangaCover())
                .memoryCachePolicy(CachePolicy.DISABLED)
                .precision(Precision.EXACT)
                .size(widthPx, heightPx)
                .scale(Scale.FILL)
                .let {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        it.transformations(RoundedCornersTransformation(roundPx))
                    } else {
                        it
                    }
                }
                .build()
            val bitmap = context.imageLoader.executeBlocking(request).image?.asDrawable(context.resources)?.toBitmap()
            id to bitmap
        }
    }

    companion object {
        /** Redraws every placed Eggiku widget */
        suspend fun refresh(context: Context = Injekt.get<Application>()) {
            try {
                androidx.glance.appwidget.GlanceAppWidgetManager(context)
                    .getGlanceIds(EggikuReadingWidget::class.java)
                    .forEach { EggikuReadingWidget().update(context, it) }
            } catch (_: Exception) {
                // No widget placed, or the launcher isn't ready
            }
        }
    }
}

class EggikuReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = EggikuReadingWidget()
}
// KMK <--
