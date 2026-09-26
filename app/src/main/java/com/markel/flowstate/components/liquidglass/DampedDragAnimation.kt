/*
 * Adapted from AndroidLiquidGlass tag 1.0.2.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified for FlowState, including safe single-tab behavior.
 */
package com.markel.flowstate.components.liquidglass

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDragCancelled: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(
        size: IntSize,
        position: Offset,
        dragAmount: Offset,
    ) -> Unit,
) {
    private val valueSpring = SpringAxis(
        initialValue = initialValue,
        dampingRatio = 1f,
        stiffness = 1000f,
        threshold = visibilityThreshold,
    )
    private val velocitySpring = SpringAxis(
        initialValue = 0f,
        dampingRatio = 0.5f,
        stiffness = 300f,
        threshold = visibilityThreshold * 10f,
    )
    private val pressSpring = SpringAxis(0f, 1f, 1000f, 0.001f)
    private val scaleXSpring = SpringAxis(initialScale, 0.6f, 250f, 0.001f)
    private val scaleYSpring = SpringAxis(initialScale, 0.7f, 250f, 0.001f)
    private val panelDragSpring = SpringAxis(0f, 1f, 300f, 0.5f)
    private var frameJob: Job? = null
    private var releaseRequested = false

    val value: Float get() = valueSpring.value
    val targetValue: Float get() = valueSpring.target
    val pressProgress: Float get() = pressSpring.value
    val scaleX: Float get() = scaleXSpring.value
    val scaleY: Float get() = scaleYSpring.value
    val velocity: Float get() = velocitySpring.value
    val panelDrag: Float get() = panelDragSpring.value

    val modifier: Modifier = Modifier.pointerInput(this) {
        inspectLiquidDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragCancelled()
                release()
            },
        ) { change, dragAmount ->
            onDrag(size, change.position, dragAmount)
        }
    }

    fun press() {
        releaseRequested = false
        pressSpring.target = 1f
        scaleXSpring.target = pressedScale
        scaleYSpring.target = pressedScale
        ensureFrameLoop()
    }

    fun release() {
        releaseRequested = true
        settlePanel()
        ensureFrameLoop()
    }

    fun updateValue(value: Float) {
        valueSpring.target = value.coerceIn(valueRange)
        ensureFrameLoop()
    }

    fun animateToValue(value: Float) {
        press()
        updateValue(value)
        release()
    }

    fun dragPanelBy(delta: Float) {
        // Pointer sampling may run faster than the display refresh rate. Keep
        // the newest simulation value here and publish it with the other
        // springs once per frame instead of invalidating Compose per event.
        panelDragSpring.snapSimulationTo(panelDragSpring.simulationValue + delta)
        ensureFrameLoop()
    }

    fun settlePanel() {
        panelDragSpring.target = 0f
        ensureFrameLoop()
    }

    private val tabsCount: Int
        get() = (valueRange.endInclusive - valueRange.start).toInt() + 1

    private fun ensureFrameLoop() {
        if (frameJob?.isActive == true) return
        frameJob = animationScope.launch {
            var previousFrameNanos = withFrameNanos { it }
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                var remaining = min(
                    (frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000_000f,
                    MaxFrameSeconds,
                )
                previousFrameNanos = frameNanos

                while (remaining > 0f) {
                    val stepSeconds = min(remaining, MaxSubstepSeconds)
                    valueSpring.step(stepSeconds)

                    val span = valueRange.endInclusive - valueRange.start
                    velocitySpring.target = if (span > 0f) valueSpring.velocity / span else 0f
                    velocitySpring.step(stepSeconds)

                    if (releaseRequested && LiquidBottomBarMotion.canCollapse(
                            value = valueSpring.simulationValue,
                            target = valueSpring.target,
                            tabsCount = tabsCount,
                        )
                    ) {
                        releaseRequested = false
                        pressSpring.target = 0f
                        scaleXSpring.target = initialScale
                        scaleYSpring.target = initialScale
                    }

                    pressSpring.step(stepSeconds)
                    scaleXSpring.step(stepSeconds)
                    scaleYSpring.step(stepSeconds)
                    panelDragSpring.step(stepSeconds)
                    remaining -= stepSeconds
                }

                valueSpring.publish()
                velocitySpring.publish()
                pressSpring.publish()
                scaleXSpring.publish()
                scaleYSpring.publish()
                panelDragSpring.publish()

                if (!releaseRequested && allSpringsAtRest()) break
            }
        }
    }

    private fun allSpringsAtRest(): Boolean =
        valueSpring.isAtRest &&
            velocitySpring.isAtRest &&
            pressSpring.isAtRest &&
            scaleXSpring.isAtRest &&
            scaleYSpring.isAtRest &&
            panelDragSpring.isAtRest

    private class SpringAxis(
        initialValue: Float,
        private val dampingRatio: Float,
        private val stiffness: Float,
        private val threshold: Float,
    ) {
        private val state = mutableFloatStateOf(initialValue)
        internal var simulationValue: Float = initialValue
            private set

        var value: Float
            get() = state.floatValue
            private set(value) {
                state.floatValue = value
            }

        var target: Float = initialValue
        var velocity: Float = 0f
            private set

        val isAtRest: Boolean
            get() = abs(simulationValue - target) < threshold &&
                abs(velocity) < threshold * RestVelocityMultiplier

        fun snapSimulationTo(value: Float) {
            simulationValue = value
            target = value
            velocity = 0f
        }

        fun step(seconds: Float) {
            if (seconds <= 0f) return
            if (isAtRest) {
                simulationValue = target
                velocity = 0f
                return
            }

            val damping = 2f * dampingRatio * sqrt(stiffness)
            val acceleration = -stiffness * (simulationValue - target) - damping * velocity
            velocity += acceleration * seconds
            simulationValue += velocity * seconds
        }

        fun publish() {
            if (state.floatValue != simulationValue) value = simulationValue
        }
    }

    private companion object {
        const val MaxFrameSeconds = 1f / 30f
        const val MaxSubstepSeconds = 1f / 240f
        const val RestVelocityMultiplier = 30f
    }
}
