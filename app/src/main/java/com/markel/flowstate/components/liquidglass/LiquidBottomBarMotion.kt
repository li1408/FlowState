package com.markel.flowstate.components.liquidglass

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure geometry used by the liquid bottom navigation implementation.
 *
 * The constants and formulas are adapted from Kyant's AndroidLiquidGlass
 * catalog component `LiquidBottomTabs` (tag 1.0.2). The source was modified
 * for FlowState and remains licensed under Apache-2.0.
 *
 * Copyright 2025 Kyant
 */
internal object LiquidBottomBarMotion {
    const val BarHeightDp = 64f
    const val DropletHeightDp = 56f
    const val HorizontalContentPaddingDp = 4f
    const val ContentClearanceDp = 72f
    const val PressedScale = 78f / DropletHeightDp

    data class Scale(val scaleX: Float, val scaleY: Float)

    fun tabWidth(
        containerWidth: Float,
        tabsCount: Int,
        horizontalContentPadding: Float = HorizontalContentPaddingDp,
    ): Float {
        if (tabsCount <= 0) return 0f
        return ((containerWidth - horizontalContentPadding * 2f) / tabsCount)
            .coerceAtLeast(0f)
    }

    fun dragTarget(
        currentTarget: Float,
        dragDelta: Float,
        tabWidth: Float,
        lastIndex: Int,
        isLtr: Boolean,
    ): Float {
        if (lastIndex <= 0 || tabWidth <= 0f) return 0f
        val direction = if (isLtr) 1f else -1f
        return (currentTarget + dragDelta / tabWidth * direction)
            .coerceIn(0f, lastIndex.toFloat())
    }

    fun snapIndex(target: Float, lastIndex: Int): Int {
        if (lastIndex <= 0) return 0
        return target.roundToInt().coerceIn(0, lastIndex)
    }

    fun dropletTranslation(
        value: Float,
        tabWidth: Float,
        tabsCount: Int,
        isLtr: Boolean,
    ): Float {
        if (tabsCount <= 1 || tabWidth <= 0f) return 0f
        val boundedValue = value.coerceIn(0f, (tabsCount - 1).toFloat())
        val visualValue = if (isLtr) boundedValue else tabsCount - 1f - boundedValue
        return visualValue * tabWidth
    }

    fun canCollapse(value: Float, target: Float, tabsCount: Int): Boolean {
        if (tabsCount <= 1) return true
        val threshold = (tabsCount - 1) * 0.025f
        return abs(value - target) < threshold
    }

    fun velocityScale(
        baseScaleX: Float,
        baseScaleY: Float,
        normalizedVelocity: Float,
    ): Scale {
        val velocity = normalizedVelocity / 10f
        val horizontal = (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
        val vertical = (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
        return Scale(
            scaleX = baseScaleX / (1f - horizontal),
            scaleY = baseScaleY * (1f - vertical),
        )
    }

    fun <T> selectedIndex(items: List<T>, selected: T): Int? =
        items.indexOf(selected).takeIf { it >= 0 }

    fun transitionDirection(fromIndex: Int, toIndex: Int, isLtr: Boolean): Int {
        val logicalDirection = (toIndex - fromIndex).coerceIn(-1, 1)
        return if (isLtr) logicalDirection else -logicalDirection
    }
}

/**
 * Keeps visual selection ahead of committed navigation while the liquid lens
 * travels. Positions are expressed in logical tab indices, so the same gate
 * works for LTR and RTL layouts.
 */
internal class LiquidBottomBarSelectionGate<T>(
    items: List<T>,
    committedKey: T,
    @Suppress("UNUSED_PARAMETER") isLtr: Boolean,
    private val onCommit: (T) -> Unit,
) {
    private var items = items.toList()
    private var targetKey: T = committedKey
    private var startPosition = items.indexOf(committedKey).coerceAtLeast(0).toFloat()
    private var lastPosition = startPosition
    private var released = false
    private var didCommitGesture = false
    private var generation = 0L
    private var commitAllowedGeneration: Long? = null

    var committedKey: T = committedKey
        private set

    var isPressed: Boolean = false
        private set

    val committedIndex: Int
        get() = items.indexOf(committedKey).coerceAtLeast(0)

    val targetIndex: Int
        get() = items.indexOf(targetKey).takeIf { it >= 0 } ?: committedIndex

    val visualTarget: T
        get() = targetKey

    fun press(index: Int) {
        val key = items.getOrNull(index) ?: return
        generation++
        startPosition = lastPosition
        targetKey = key
        released = false
        didCommitGesture = false
        commitAllowedGeneration = null
        isPressed = true
    }

    fun release(): Long {
        isPressed = false
        released = true
        commitIfReady(lastPosition)
        return generation
    }

    /**
     * Opens the irreversible navigation gate for one released gesture.
     * A newer press increments [generation], so a delayed permission from an
     * older rapid tap cannot navigate to an intermediate destination.
     */
    fun allowCommit(generation: Long) {
        if (generation != this.generation) return
        commitAllowedGeneration = generation
        commitIfReady(lastPosition)
    }

    fun retarget(index: Int) {
        val key = items.getOrNull(index) ?: return
        targetKey = key
    }

    fun updatePosition(position: Float) {
        lastPosition = position
        commitIfReady(position)
    }

    fun cancel() {
        generation++
        isPressed = false
        released = false
        didCommitGesture = true
        commitAllowedGeneration = null
        targetKey = committedKey
        startPosition = lastPosition
    }

    fun updateItems(items: List<T>) {
        if (items.isEmpty()) return

        this.items = items.toList()
        if (committedKey !in this.items) {
            committedKey = this.items.first()
        }
        if (targetKey !in this.items) {
            targetKey = committedKey
        }

        val position = committedIndex.toFloat()
        if (!isPressed && !released) {
            startPosition = position
            lastPosition = position
        }
    }

    fun synchronizeCommitted(key: T, position: Float) {
        if (key !in items) return
        generation++
        committedKey = key
        targetKey = key
        isPressed = false
        released = false
        didCommitGesture = true
        commitAllowedGeneration = null
        startPosition = position
        lastPosition = position
    }

    private fun commitIfReady(position: Float) {
        if (!released ||
            didCommitGesture ||
            commitAllowedGeneration != generation ||
            targetKey == committedKey
        ) {
            return
        }

        val distance = targetIndex - startPosition
        if (distance == 0f) return
        val progress = (position - startPosition) / distance
        if (progress <= CommitProgress) return

        didCommitGesture = true
        released = false
        commitAllowedGeneration = null
        committedKey = targetKey
        onCommit(committedKey)
    }

    private companion object {
        const val CommitProgress = 0.40f
    }
}
