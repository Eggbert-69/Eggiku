package eu.kanade.presentation.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.components.material.LocalGlassEnabled
import tachiyomi.presentation.core.components.material.TabText

@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    // KMK -->
    val glassEnabled = LocalGlassEnabled.current
    // KMK <--
    Column(modifier = Modifier.zIndex(2f)) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
            // KMK -->
            indicator = {
                if (!glassEnabled) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(currentPageIndex, matchContentSize = true),
                        width = Dp.Unspecified,
                    )
                }
            },
            // KMK <--
        ) {
            categories.forEachIndexed { index, category ->
                // KMK -->
                val pillColor by animateColorAsState(
                    targetValue = if (glassEnabled && currentPageIndex == index) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "tabPill",
                )
                // KMK <--
                Tab(
                    // KMK -->
                    modifier = if (glassEnabled) {
                        Modifier
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .clip(CircleShape)
                            .background(pillColor)
                    } else {
                        Modifier
                    },
                    // KMK <--
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        TabText(
                            text = category.visualName,
                            badgeCount = getItemCountForCategory(category),
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // KMK -->
        if (!glassEnabled) {
            // KMK <--
            HorizontalDivider()
            // KMK -->
        }
        // KMK <--
    }
}
