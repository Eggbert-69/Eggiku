package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.LocalGlassEnabled
import tachiyomi.presentation.core.components.material.LocalScaffoldExtraBottomInset
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import tachiyomi.presentation.core.i18n.pluralStringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object HomeScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = HomeScreen

    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    private val TABS = listOf(
        LibraryTab,
        UpdatesTab,
        HistoryTab,
        BrowseTab,
        MoreTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // SY -->
        val scope = rememberCoroutineScope()
        val alwaysShowLabel by remember {
            Injekt.get<UiPreferences>().bottomBarLabels().asState(scope)
        }
        // SY <--

        TabNavigator(
            tab = LibraryTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Scaffold(
                    startBar = {
                        if (isTabletUi()) {
                            NavigationRail {
                                TABS
                                    // SY -->
                                    .fastFilter { it.isEnabled() }
                                    // SY <--
                                    .fastForEach {
                                        NavigationRailItem(it/* SY --> */, alwaysShowLabel/* SY <-- */)
                                    }
                            }
                        }
                    },
                    bottomBar = {
                        if (!isTabletUi()) {
                            val bottomNavVisible by produceState(initialValue = true) {
                                showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                            }
                            AnimatedVisibility(
                                visible = bottomNavVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                NavigationBar {
                                    TABS
                                        // SY -->
                                        .fastFilter { it.isEnabled() }
                                        // SY <--
                                        .fastForEach {
                                            NavigationBarItem(it/* SY --> */, alwaysShowLabel/* SY <-- */)
                                        }
                                }
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets(0),
                ) { contentPadding ->
                    // KMK -->
                    // With glass on, tabs draw behind the floating navigation bar and keep its height clear
                    val glassEnabled = LocalGlassEnabled.current
                    val layoutDirection = LocalLayoutDirection.current
                    val bodyPadding = if (glassEnabled) {
                        PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            top = contentPadding.calculateTopPadding(),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                        )
                    } else {
                        contentPadding
                    }
                    val extraBottomInset = if (glassEnabled) contentPadding.calculateBottomPadding() else 0.dp
                    // KMK <--
                    Box(
                        modifier = Modifier
                            .padding(/* KMK --> */bodyPadding/* KMK <-- */)
                            .consumeWindowInsets(contentPadding),
                    ) {
                        AnimatedContent(
                            targetState = tabNavigator.current,
                            transitionSpec = {
                                materialFadeThroughIn(initialScale = 1f, durationMillis = TabFadeDuration) togetherWith
                                    materialFadeThroughOut(durationMillis = TabFadeDuration)
                            },
                            label = "tabContent",
                            contentKey = { it.key },
                        ) {
                            tabNavigator.saveableState(key = "currentTab", it) {
                                // KMK -->
                                CompositionLocalProvider(LocalScaffoldExtraBottomInset provides extraBottomInset) {
                                    // KMK <--
                                    it.Content()
                                    // KMK -->
                                }
                                // KMK <--
                            }
                        }
                    }
                }
            }

            val goToLibraryTab = { tabNavigator.current = LibraryTab }

            BackHandler(enabled = tabNavigator.current != LibraryTab, onBack = goToLibraryTab)

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToLibraryTab()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current = when (it) {
                            is Tab.Library -> LibraryTab
                            Tab.Updates -> UpdatesTab
                            Tab.History -> HistoryTab
                            is Tab.Browse -> {
                                if (it.toExtensions) {
                                    BrowseTab.showExtension()
                                }
                                BrowseTab
                            }
                            is Tab.More -> MoreTab
                        }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More) {
                            if (it.toDownloads) {
                                navigator.push(DownloadQueueScreen)
                                // KMK -->
                            } else if (it.toLibraryUpdateErrors) {
                                navigator.push(LibraryUpdateErrorScreen())
                                // KMK <--
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        // SY -->
        alwaysShowLabel: Boolean,
        // SY <--
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = /* SY --> */alwaysShowLabel, /* SY <-- */
        )
    }

    @Composable
    fun NavigationRailItem(
        tab: eu.kanade.presentation.util.Tab,
        // SY -->
        alwaysShowLabel: Boolean,
        // SY <--
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = /* SY --> */alwaysShowLabel, /* SY <-- */
        )
    }

    @Composable
    private fun NavigationIconItem(tab: eu.kanade.presentation.util.Tab) {
        BadgedBox(
            badge = {
                when {
                    tab is UpdatesTab -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newShowUpdatesCount().changes(),
                                pref.newUpdatesCount().changes(),
                            ) { show, count -> if (show) count else 0 }
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            Injekt.get<SourcePreferences>().extensionUpdatesCount().changes()
                                .collectLatest { value = it }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
                // KMK -->
                modifier = Modifier.eggikuTabTapAnimation(tab),
                // KMK <--
            )
        }
    }

    // KMK -->
    /**
     * Eggiku: each tab icon does its own little move when you switch to it. The bookcase rocks
     * (the egg wobbles), the bell jingles, the portal door stretches open; the others pop.
     */
    @Composable
    private fun Modifier.eggikuTabTapAnimation(tab: eu.kanade.presentation.util.Tab): Modifier {
        val tabNavigator = LocalTabNavigator.current
        val selected = tabNavigator.current::class == tab::class
        val reduceMotion = rememberReduceMotion()
        val rotation = remember { Animatable(0f) }
        val scaleX = remember { Animatable(1f) }
        val scaleY = remember { Animatable(1f) }
        var wasSelected by remember { mutableStateOf(selected) }
        LaunchedEffect(selected) {
            val justSelected = selected && !wasSelected
            wasSelected = selected
            if (!justSelected || reduceMotion) return@LaunchedEffect
            when (tab) {
                is LibraryTab -> rotation.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 420
                        0f at 0
                        -9f at 90
                        7f at 200
                        -3f at 310
                        0f at 420
                    },
                )
                is UpdatesTab -> rotation.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 520
                        0f at 0
                        16f at 70
                        -13f at 160
                        9f at 250
                        -5f at 340
                        2f at 430
                        0f at 520
                    },
                )
                is BrowseTab -> coroutineScope {
                    launch {
                        scaleY.animateTo(
                            1f,
                            keyframes {
                                durationMillis = 360
                                1f at 0
                                0.78f at 90
                                1.16f at 210
                                1f at 360
                            },
                        )
                    }
                    scaleX.animateTo(
                        1f,
                        keyframes {
                            durationMillis = 360
                            1f at 0
                            1.14f at 90
                            0.92f at 210
                            1f at 360
                        },
                    )
                }
                else -> coroutineScope {
                    val pop = keyframes<Float> {
                        durationMillis = 240
                        1f at 0
                        1.18f at 90
                        0.96f at 170
                        1f at 240
                    }
                    launch { scaleY.animateTo(1f, pop) }
                    scaleX.animateTo(1f, pop)
                }
            }
        }
        return this.graphicsLayer {
            rotationZ = rotation.value
            this.scaleX = scaleX.value
            this.scaleY = scaleY.value
            // The bell swings from its top; the others from the bottom, like they're sitting on a shelf
            transformOrigin = if (tab is UpdatesTab) TransformOrigin(0.5f, 0.1f) else TransformOrigin(0.5f, 0.9f)
        }
    }
    // KMK <--

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false) : Tab
        data class More(
            val toDownloads: Boolean,
            // KMK -->
            val toLibraryUpdateErrors: Boolean = false,
            // KMK <--
        ) : Tab
    }
}
