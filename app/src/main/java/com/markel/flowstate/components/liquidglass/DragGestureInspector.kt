/*
 * Adapted from AndroidLiquidGlass tag 1.0.2.
 * Copyright 2025 Kyant. Licensed under Apache-2.0.
 * Modified for FlowState package naming and gesture integration.
 */
package com.markel.flowstate.components.liquidglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull

internal suspend fun PointerInputScope.inspectLiquidDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )

        var terminalCallbackDispatched = false
        try {
            onDragStart(down)
            onDrag(down, Offset.Zero)
            down.consume()

            val upEvent = dragUntilUp(down.id) { change ->
                onDrag(change, change.positionChange())
                change.consume()
            }
            if (upEvent == null) {
                terminalCallbackDispatched = true
                onDragCancel()
            } else {
                terminalCallbackDispatched = true
                onDragEnd(upEvent)
                upEvent.consume()
            }
        } finally {
            // pointerInput is cancelled when its keys change (for example on
            // resize or layout-direction changes). Always unwind the press so
            // the liquid lens cannot remain enlarged with a stale target.
            if (!terminalCallbackDispatched) onDragCancel()
        }
    }
}

private suspend inline fun AwaitPointerEventScope.dragUntilUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) {
        return null
    }

    var pointer = pointerId
    while (true) {
        val change = awaitLiquidDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change

        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitLiquidDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
