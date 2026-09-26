package com.markel.flowstate.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.kyant.backdrop.Backdrop
import com.markel.flowstate.components.liquidglass.LiquidBottomTab
import com.markel.flowstate.components.liquidglass.LiquidBottomBarMotion
import com.markel.flowstate.components.liquidglass.LiquidBottomTabs
import com.markel.flowstate.navigation.BottomNavScreen
import com.markel.flowstate.navigation.TabKey

/**
 * Floating liquid-glass navigation adapted from AndroidLiquidGlass' catalog.
 *
 * Navigation remains driven by the app's stable [NavKey] values, while the
 * glass component owns only transient press, drag and settle animation state.
 */
@Composable
fun FlowBottomBar(
    topLevelRoute: NavKey,
    onNavigate: (key: NavKey, visualDirection: Int) -> Unit,
    isLandscape: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    items: List<BottomNavScreen> = emptyList(),
) {
    if (items.isEmpty()) return

    val selectedIndex = LiquidBottomBarMotion.selectedIndex(
        items = items.map { it.key },
        selected = topLevelRoute,
    )
        ?: 0
    val accentColor = Color(0xFF12A66A)
    val baseContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    val hapticFeedback = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val itemKeys = remember(items) { items.map { it.key } }
    var committedIndex by remember(itemKeys) { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) { committedIndex = selectedIndex }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        LiquidBottomTabs(
            selectedTabIndex = selectedIndex,
            onTabSelected = { index ->
                val destination = items.getOrNull(index) ?: return@LiquidBottomTabs
                if (index != committedIndex) {
                    val visualDirection = LiquidBottomBarMotion.transitionDirection(
                        fromIndex = committedIndex,
                        toIndex = index,
                        isLtr = isLtr,
                    )
                    committedIndex = index
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate(destination.key, visualDirection)
                }
            },
            backdrop = backdrop,
            tabsCount = items.size,
            accentColor = accentColor,
            contentColor = baseContentColor,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.72f else 1f)
                .widthIn(max = 560.dp),
        ) { visualSelectedIndex, committedSelectedIndex ->
            items.forEachIndexed { index, screen ->
                val visuallySelected = index == visualSelectedIndex
                val semanticallySelected = index == committedSelectedIndex
                val label = stringResource(screen.labelRes)
                val iconDrawable = if (visuallySelected) screen.iconSelectedRes else screen.iconRes

                LiquidBottomTab(
                    index = index,
                    selected = semanticallySelected,
                    modifier = Modifier.testTag(screen.benchmarkTag()),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(iconDrawable),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (visuallySelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun BottomNavScreen.benchmarkTag(): String = when (key) {
    TabKey.Tasks -> "benchmark_bottom_tab_flow"
    TabKey.Calendar -> "benchmark_bottom_tab_calendar"
    TabKey.Habits -> "benchmark_bottom_tab_habits"
    TabKey.Mood -> "benchmark_bottom_tab_mood"
    TabKey.Settings -> "benchmark_bottom_tab_settings"
}
