/*
 * Adapted from AndroidLiquidGlass tag 1.0.2.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified for FlowState accessibility and indexed selection.
 */
package com.markel.flowstate.components.liquidglass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule

private val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }
private val LocalLiquidBottomTabSelection = staticCompositionLocalOf<(Int) -> Unit> {
    error("LiquidBottomTab must be hosted by LiquidBottomTabs")
}

@Composable
internal fun RowScope.LiquidBottomTab(
    index: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    val select = LocalLiquidBottomTabSelection.current

    Column(
        modifier = modifier
            .clip(ContinuousCapsule)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { select(index) },
            )
            .semantics(mergeDescendants = true) {}
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val contentScale = scale()
                scaleX = contentScale
                scaleY = contentScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
internal fun ProvideLiquidBottomTabInteraction(
    scale: () -> Float,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiquidBottomTabScale provides scale,
        LocalLiquidBottomTabSelection provides onSelect,
        content = content,
    )
}
