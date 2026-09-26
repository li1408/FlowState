/*
 * Adapted from AndroidLiquidGlass tag 1.0.2 LiquidBottomTabs.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified for FlowState colors, dynamic tabs and external navigation state.
 */
package com.markel.flowstate.components.liquidglass

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
internal fun LiquidBottomTabs(
    selectedTabIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(visualSelectedIndex: Int, committedIndex: Int) -> Unit,
) {
    if (tabsCount <= 0) return

    val isLightTheme = contentColor.luminance() < 0.5f
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.40f)
    } else {
        Color(0xFF121212).copy(alpha = 0.40f)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val latestOnTabSelected = rememberUpdatedState(onTabSelected)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = LiquidBottomBarMotion.tabWidth(
            containerWidth = constraints.maxWidth.toFloat(),
            tabsCount = tabsCount,
            horizontalContentPadding = with(density) {
                LiquidBottomBarMotion.HorizontalContentPaddingDp.dp.toPx()
            },
        )
        val horizontalContentPadding = with(density) {
            LiquidBottomBarMotion.HorizontalContentPaddingDp.dp.toPx()
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val safeSelectedIndex = selectedTabIndex.coerceIn(0, tabsCount - 1)
        var currentIndex by remember(tabsCount) { mutableIntStateOf(safeSelectedIndex) }

        val selectionGate = remember(tabsCount, isLtr) {
            LiquidBottomBarSelectionGate(
                items = (0 until tabsCount).toList(),
                committedKey = safeSelectedIndex,
                isLtr = isLtr,
                onCommit = { index ->
                    currentIndex = index
                    latestOnTabSelected.value(index)
                },
            )
        }
        val commitWindow = remember(animationScope, selectionGate) {
            LatestCommitWindow(
                scope = animationScope,
                allowCommit = selectionGate::allowCommit,
            )
        }
        DisposableEffect(commitWindow) {
            onDispose(commitWindow::cancel)
        }

        val dragAnimation = remember(animationScope, tabsCount, tabWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = safeSelectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = LiquidBottomBarMotion.PressedScale,
                onDragStarted = { position ->
                    commitWindow.cancel()
                    val pressedIndex = hitTestIndex(
                        pointerX = position.x,
                        tabWidth = tabWidth,
                        horizontalContentPadding = horizontalContentPadding,
                        lastIndex = tabsCount - 1,
                        isLtr = isLtr,
                    )
                    selectionGate.press(pressedIndex)
                    updateValue(pressedIndex.toFloat())
                },
                onDragStopped = {
                    val targetIndex = LiquidBottomBarMotion.snapIndex(
                        target = targetValue,
                        lastIndex = tabsCount - 1,
                    )
                    selectionGate.retarget(targetIndex)
                    updateValue(targetIndex.toFloat())
                    commitWindow.schedule(selectionGate.release())
                    settlePanel()
                },
                onDragCancelled = {
                    commitWindow.cancel()
                    selectionGate.cancel()
                    updateValue(currentIndex.toFloat())
                    settlePanel()
                },
                onDrag = { _, _, dragAmount ->
                    val nextTarget = LiquidBottomBarMotion.dragTarget(
                        currentTarget = targetValue,
                        dragDelta = dragAmount.x,
                        tabWidth = tabWidth,
                        lastIndex = tabsCount - 1,
                        isLtr = isLtr,
                    )
                    val focusedIndex = LiquidBottomBarMotion.snapIndex(
                        target = nextTarget,
                        lastIndex = tabsCount - 1,
                    )
                    selectionGate.retarget(focusedIndex)
                    updateValue(nextTarget)
                    dragPanelBy(dragAmount.x)
                },
            )
        }

        // The colored icon and label move with the spring-driven droplet,
        // rather than jumping to the raw pointer target before the glass gets
        // there. Accessibility selection remains tied to [currentIndex].
        val visualIndex by remember(dragAnimation, tabsCount) {
            derivedStateOf {
                LiquidBottomBarMotion.snapIndex(
                    target = dragAnimation.value,
                    lastIndex = tabsCount - 1,
                )
            }
        }

        val panelOffset by remember(density, constraints.maxWidth, dragAnimation) {
            derivedStateOf {
                val fraction = if (constraints.maxWidth == 0) {
                    0f
                } else {
                    (dragAnimation.panelDrag / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                }
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        LaunchedEffect(dragAnimation, selectionGate) {
            snapshotFlow { dragAnimation.value }
                .collect(selectionGate::updatePosition)
        }

        LaunchedEffect(safeSelectedIndex, dragAnimation, commitWindow) {
            val routeChanged = currentIndex != safeSelectedIndex
            commitWindow.cancel()
            currentIndex = safeSelectedIndex
            selectionGate.synchronizeCommitted(
                key = safeSelectedIndex,
                position = dragAnimation.value,
            )
            if (routeChanged && abs(dragAnimation.targetValue - safeSelectedIndex) > 0.001f) {
                dragAnimation.animateToValue(safeSelectedIndex.toFloat())
            }
        }

        val selectTab: (Int) -> Unit = remember(
            dragAnimation,
            selectionGate,
            commitWindow,
            tabsCount,
        ) {
            { requestedIndex ->
                val targetIndex = requestedIndex.coerceIn(0, tabsCount - 1)
                commitWindow.cancel()
                selectionGate.press(targetIndex)
                dragAnimation.press()
                dragAnimation.updateValue(targetIndex.toFloat())
                commitWindow.schedule(selectionGate.release())
                dragAnimation.release()
            }
        }

        val interactiveHighlight = remember(
            dragAnimation,
            isLtr,
            tabWidth,
        ) {
            InteractiveHighlight(
                progress = { dragAnimation.pressProgress },
                position = { size, _ ->
                    Offset(
                        x = if (isLtr) {
                            (dragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        y = size.height / 2f,
                    )
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LiquidBottomBarMotion.BarHeightDp.dp)
                .then(dragAnimation.modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideLiquidBottomTabInteraction(scale = { 1f }, onSelect = selectTab) {
                    Row(
                        modifier = Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            layerBlock = {
                                val scale = lerp(
                                    1f,
                                    1f + 16.dp.toPx() / size.width,
                                    dragAnimation.pressProgress,
                                )
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .semantics { selectableGroup() }
                            .height(LiquidBottomBarMotion.BarHeightDp.dp)
                            .fillMaxWidth()
                            .padding(LiquidBottomBarMotion.HorizontalContentPaddingDp.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        content(visualIndex, currentIndex)
                    }
                }
            }

            ProvideLiquidBottomTabInteraction(
                scale = { lerp(1f, 1.2f, dragAnimation.pressProgress) },
                onSelect = selectTab,
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                val progress = dragAnimation.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(LiquidBottomBarMotion.DropletHeightDp.dp)
                        .fillMaxWidth()
                        .padding(horizontal = LiquidBottomBarMotion.HorizontalContentPaddingDp.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content(visualIndex, currentIndex)
                }
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = LiquidBottomBarMotion.HorizontalContentPaddingDp.dp)
                    .graphicsLayer {
                        translationX = if (isLtr) {
                            LiquidBottomBarMotion.dropletTranslation(
                                value = dragAnimation.value,
                                tabWidth = tabWidth,
                                tabsCount = tabsCount,
                                isLtr = true,
                            ) + panelOffset
                        } else {
                            LiquidBottomBarMotion.dropletTranslation(
                                value = dragAnimation.value,
                                tabWidth = tabWidth,
                                tabsCount = tabsCount,
                                isLtr = false,
                            ) + panelOffset
                        }
                    }
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            val progress = dragAnimation.pressProgress
                            lens(
                                refractionHeight = 10.dp.toPx() * progress,
                                refractionAmount = 14.dp.toPx() * progress,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                        },
                        shadow = { Shadow(alpha = dragAnimation.pressProgress) },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * dragAnimation.pressProgress,
                                alpha = dragAnimation.pressProgress,
                            )
                        },
                        layerBlock = {
                            val scale = LiquidBottomBarMotion.velocityScale(
                                baseScaleX = dragAnimation.scaleX,
                                baseScaleY = dragAnimation.scaleY,
                                normalizedVelocity = dragAnimation.velocity,
                            )
                            scaleX = scale.scaleX
                            scaleY = scale.scaleY
                        },
                        onDrawSurface = {
                            val progress = dragAnimation.pressProgress
                            drawRect(
                                color = if (isLightTheme) {
                                    Color.Black.copy(alpha = 0.10f)
                                } else {
                                    Color.White.copy(alpha = 0.10f)
                                },
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .height(LiquidBottomBarMotion.DropletHeightDp.dp)
                    .fillMaxWidth(1f / tabsCount),
            )
        }
    }
}

/**
 * Keeps navigation reversible for a brief rapid-tap window. The liquid must
 * still cross the 40% travel gate; this only ensures a newer physical press
 * can replace an older released target before navigation becomes irreversible.
 */
private class LatestCommitWindow(
    private val scope: CoroutineScope,
    private val allowCommit: (Long) -> Unit,
) {
    private var job: Job? = null

    fun schedule(generation: Long) {
        job?.cancel()
        job = scope.launch {
            delay(CommitCoalescingMillis)
            allowCommit(generation)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

private const val CommitCoalescingMillis = 100L

private fun hitTestIndex(
    pointerX: Float,
    tabWidth: Float,
    horizontalContentPadding: Float,
    lastIndex: Int,
    isLtr: Boolean,
): Int {
    if (lastIndex <= 0 || tabWidth <= 0f) return 0
    val visualIndex = ((pointerX - horizontalContentPadding) / tabWidth)
        .toInt()
        .coerceIn(0, lastIndex)
    return if (isLtr) visualIndex else lastIndex - visualIndex
}
