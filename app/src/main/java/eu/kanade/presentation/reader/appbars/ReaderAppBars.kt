package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.music.EggikuReaderMusic
import eu.kanade.presentation.reader.music.rememberEggikuReaderMusicState
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

// SY -->
enum class NavBarType {
    VerticalRight,
    VerticalLeft,
    Bottom,
}
// SY <--

@Composable
fun ReaderAppBars(
    visible: Boolean,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    viewer: Viewer?,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    isExhToolsVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
    navBarType: NavBarType,
    currentPageText: String,
    enabledButtons: ImmutableSet<String>,
    currentReadingMode: ReadingMode,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickChapterList: () -> Unit,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,
    // SY <--
    // KMK -->
    mangaId: Long? = null,
    chapterId: Long? = null,
    // KMK <--
) {
    // KMK -->
    val musicState = rememberEggikuReaderMusicState()
    val musicContext = LocalContext.current
    val musicScope = rememberCoroutineScope()
    // KMK <--
    val isRtl = viewer is R2LPagerViewer
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

    // KMK -->
    val oneHandMode by remember { Injekt.get<ReaderPreferences>().eggikuOneHandMode() }.collectAsState()
    // One-hand mode moves the title bar and the chapter slider down to the bottom, in thumb reach
    val effectiveNavBarType = if (oneHandMode) NavBarType.Bottom else navBarType
    val topBars: @Composable (Modifier) -> Unit = { topBarsModifier ->
        // SY -->
        Column(modifier = topBarsModifier) {
            // SY <--
            ReaderTopBar(
                modifier = Modifier
                    .background(backgroundColor)
                    .clickable(onClick = onClickTopAppBar),
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                navigateUp = navigateUp,
                bookmarked = bookmarked,
                onToggleBookmarked = onToggleBookmarked,
                // SY -->
                onOpenInWebView = null, // onOpenInWebView,
                onOpenInBrowser = null, // onOpenInBrowser,
                onShare = null, // onShare,
                // SY <--
            )
            // SY -->
            ExhUtils(
                isVisible = isExhToolsVisible,
                onSetExhUtilsVisibility = onSetExhUtilsVisibility,
                backgroundColor = backgroundColor,
                isAutoScroll = isAutoScroll,
                isAutoScrollEnabled = isAutoScrollEnabled,
                onToggleAutoscroll = onToggleAutoscroll,
                autoScrollFrequency = autoScrollFrequency,
                onSetAutoScrollFrequency = onSetAutoScrollFrequency,
                onClickAutoScrollHelp = onClickAutoScrollHelp,
                onClickRetryAll = onClickRetryAll,
                onClickRetryAllHelp = onClickRetryAllHelp,
                onClickBoostPage = onClickBoostPage,
                onClickBoostPageHelp = onClickBoostPageHelp,
            )
        }
        // SY <--
    }
    // KMK <--

    Column(modifier = Modifier.fillMaxHeight()) {
        // KMK -->
        if (!oneHandMode) {
            // KMK <--
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeIn(animationSpec = readerBarsFadeAnimationSpec),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
                    fadeOut(animationSpec = readerBarsFadeAnimationSpec),
            ) {
                // KMK -->
                topBars(Modifier)
                // KMK <--
            }
            // KMK -->
        }
        // KMK <--

        // KMK -->
        when (effectiveNavBarType) {
            NavBarType.VerticalLeft -> {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = readerBarsSlideAnimationSpec,
                    ) +
                        fadeIn(animationSpec = readerBarsFadeAnimationSpec),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = readerBarsSlideAnimationSpec,
                    ) +
                        fadeOut(animationSpec = readerBarsFadeAnimationSpec),
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.Start),
                ) {
                    ChapterNavigator(
                        isRtl = isRtl,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        // SY -->
                        isVerticalSlider = true,
                        currentPageText = currentPageText,
                        // SY <--
                    )
                }
            }

            NavBarType.VerticalRight -> {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = readerBarsSlideAnimationSpec,
                    ) +
                        fadeIn(animationSpec = readerBarsFadeAnimationSpec),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = readerBarsSlideAnimationSpec,
                    ) +
                        fadeOut(animationSpec = readerBarsFadeAnimationSpec),
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.End),
                ) {
                    ChapterNavigator(
                        isRtl = isRtl,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        // SY -->
                        isVerticalSlider = true,
                        currentPageText = currentPageText,
                        // SY <--
                    )
                }
            }
            // KMK <--
            else -> Spacer(modifier = Modifier.weight(1f))
        }

        // KMK -->
        EggikuReaderMusic(
            state = musicState,
            menuVisible = visible,
            mangaId = mangaId,
            chapterId = chapterId,
            modifier = if (visible) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        )
        // KMK <--

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeIn(animationSpec = readerBarsFadeAnimationSpec),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
                fadeOut(animationSpec = readerBarsFadeAnimationSpec),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                // KMK -->
                if (oneHandMode) topBars(Modifier.consumeWindowInsets(WindowInsets.statusBars))
                // KMK <--
                // SY -->
                if (effectiveNavBarType == NavBarType.Bottom) {
                    // SY <--
                    ChapterNavigator(
                        isRtl = isRtl,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        // SY -->
                        isVerticalSlider = false,
                        currentPageText = currentPageText,
                        // SY <--
                    )
                }
                ReaderBottomBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = MaterialTheme.padding.small)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    readingMode = readingMode,
                    onClickReadingMode = onClickReadingMode,
                    orientation = orientation,
                    onClickOrientation = onClickOrientation,
                    cropEnabled = cropEnabled,
                    onClickCropBorder = onClickCropBorder,
                    onClickSettings = onClickSettings,
                    // SY -->
                    enabledButtons = enabledButtons,
                    currentReadingMode = currentReadingMode,
                    dualPageSplitEnabled = dualPageSplitEnabled,
                    doublePages = doublePages,
                    onClickChapterList = onClickChapterList,
                    onClickWebView = onOpenInWebView,
                    onClickBrowser = onOpenInBrowser,
                    onClickShare = onShare,
                    onClickPageLayout = onClickPageLayout,
                    onClickShiftPage = onClickShiftPage,
                    // SY <--
                    // KMK -->
                    onClickMusic = { musicState.onMusicButton(musicContext, musicScope) },
                    // KMK <--
                )
            }
        }
    }
}
