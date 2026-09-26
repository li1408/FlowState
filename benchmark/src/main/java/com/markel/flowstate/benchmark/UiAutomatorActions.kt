package com.markel.flowstate.benchmark

import android.app.UiAutomation
import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlin.math.abs

private const val UI_TIMEOUT_MS = 5_000L

internal fun MacrobenchmarkScope.resetBenchmarkFixtures() =
    resetBenchmarkFixturesOnDevice(device)

internal fun resetBenchmarkFixturesOnDevice(
    device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
) {
    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    val result = device.executeShellCommand(
        "am start -W -n $TARGET_PACKAGE/com.markel.flowstate.MainActivity " +
            "--ez $RESET_FIXTURES_EXTRA true"
    )
    check("Status: ok" in result || "Status: timeout" in result) {
        "Unable to launch the isolated benchmark fixture reset: $result"
    }
    check(
        device.wait(
            Until.hasObject(By.res(BenchmarkTags.FLOW_TAB)),
            UI_TIMEOUT_MS,
        )
    ) { "Timed out waiting for the benchmark fixture reset to finish" }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.waitForTag(tag: String): UiObject2 =
    device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MS)
        ?: error("Timed out waiting for UI tag: $tag")

internal fun MacrobenchmarkScope.waitForEnabledTag(
    tag: String,
    fixtureRequirement: String,
): UiObject2 = device.wait(
    Until.findObject(By.res(tag).enabled(true)),
    UI_TIMEOUT_MS,
) ?: error(
    "Timed out waiting for an enabled UI tag: $tag. " +
        "Required benchmark fixture: $fixtureRequirement",
)

internal fun MacrobenchmarkScope.tapTag(tag: String) {
    waitForTag(tag).click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.flingTag(tag: String) {
    waitForTag(tag).apply {
        setGestureMargin(device.displayWidth / 5)
        fling(Direction.DOWN)
        device.waitForIdle()
        fling(Direction.UP)
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.tapEnabledTag(
    tag: String,
    fixtureRequirement: String,
) {
    waitForEnabledTag(tag, fixtureRequirement).click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.tapEnabledTagRapidly(
    tag: String,
    count: Int,
    fixtureRequirement: String,
    preferredY: Int? = null,
): Point {
    val target = if (preferredY == null) {
        waitForEnabledTag(tag, fixtureRequirement)
    } else {
        device.wait(Until.hasObject(By.res(tag).enabled(true)), UI_TIMEOUT_MS)
        device.findObjects(By.res(tag).enabled(true))
            .minByOrNull { abs(it.visibleCenter.y - preferredY) }
            ?: error(
                "Timed out waiting for an enabled UI tag near y=$preferredY: $tag. " +
                    "Required benchmark fixture: $fixtureRequirement",
            )
    }
    val center = target.visibleCenter
    repeat(count) {
        check(device.click(center.x, center.y)) {
            "Unable to inject tap $it for enabled UI tag: $tag"
        }
    }
    device.waitForIdle()
    return center
}

internal fun MacrobenchmarkScope.longPressDragFirstToLast(
    tag: String,
    listTag: String,
) {
    check(tag == BenchmarkTags.HABIT_REORDER_HANDLE)
    longPressDragAcrossList(
        sourceTag = BenchmarkTags.HABIT_REORDER_FIRST_AT_START,
        destinationStateTag = BenchmarkTags.HABIT_REORDER_FIRST_AT_END,
        listTag = listTag,
        firstToLast = true,
    )
}

internal fun MacrobenchmarkScope.longPressDragLastToFirst(
    tag: String,
    listTag: String,
) {
    check(tag == BenchmarkTags.HABIT_REORDER_HANDLE)
    longPressDragAcrossList(
        // The exact same stable fixture row must return from visual index 29
        // to index 0; merely seeing another row is not sufficient evidence.
        sourceTag = BenchmarkTags.HABIT_REORDER_FIRST_AT_END,
        destinationStateTag = BenchmarkTags.HABIT_REORDER_FIRST_AT_START,
        listTag = listTag,
        firstToLast = false,
    )
}

private fun MacrobenchmarkScope.longPressDragAcrossList(
    sourceTag: String,
    destinationStateTag: String,
    listTag: String,
    firstToLast: Boolean,
) {
    val list = waitForTag(listTag).apply {
        setGestureMargin(device.displayWidth / 5)
    }
    val source = device.findObject(By.res(sourceTag)) ?: list.scrollUntil(
        if (firstToLast) Direction.UP else Direction.DOWN,
        Until.findObject(By.res(sourceTag)),
    ) ?: error(
        "Unable to scroll the exact reorder source into view: $sourceTag. " +
            "The deterministic 30-item fixture or its visual order is incomplete",
    )
    device.waitForIdle()
    val listBounds = waitForTag(listTag).visibleBounds
    val from = source.visibleCenter
    val edgeInset = 24
    val to = Point(
        from.x,
        if (firstToLast) listBounds.bottom - edgeInset else listBounds.top + edgeInset,
    )
    val reachedDestination = injectLongPressDrag(
        from = from,
        to = to,
        destinationReached = { device.hasObject(By.res(destinationStateTag)) },
    )
    check(reachedDestination) {
        "Habit reorder did not reach exact state $destinationStateTag within " +
            "${EDGE_TIMEOUT_MILLIS}ms; the 30-item journey is incomplete"
    }
    device.waitForIdle()
    check(device.hasObject(By.res(destinationStateTag))) {
        "Dragged habit did not retain exact state $destinationStateTag after the drop"
    }
}

private fun injectLongPressDrag(
    from: Point,
    to: Point,
    holdMillis: Long = 550L,
    dragMillis: Long = 400L,
    steps: Int = 24,
    destinationReached: () -> Boolean,
): Boolean {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val downTime = SystemClock.uptimeMillis()
    var pointerIsDown = false
    try {
        automation.injectTouch(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            point = from,
        )
        pointerIsDown = true
        SystemClock.sleep(holdMillis)

        repeat(steps) { index ->
            val fraction = (index + 1f) / steps
            val point = Point(
                (from.x + (to.x - from.x) * fraction).toInt(),
                (from.y + (to.y - from.y) * fraction).toInt(),
            )
            val eventTime = SystemClock.uptimeMillis()
            automation.injectTouch(
                downTime = downTime,
                eventTime = eventTime,
                action = MotionEvent.ACTION_MOVE,
                point = point,
            )
            SystemClock.sleep(dragMillis / steps)
        }

        // Keep pulsing at the LazyColumn edge until the dragged fixture ID is
        // actually exposed at visual index 29 (or index 0 on the return leg).
        val deadline = SystemClock.uptimeMillis() + EDGE_TIMEOUT_MILLIS
        var pulse = 0
        var reachedDestination = destinationReached()
        while (!reachedDestination && SystemClock.uptimeMillis() < deadline) {
            val edgePoint = Point(to.x, to.y + if (pulse % 2 == 0) 1 else -1)
            automation.injectTouch(
                downTime = downTime,
                eventTime = SystemClock.uptimeMillis(),
                action = MotionEvent.ACTION_MOVE,
                point = edgePoint,
            )
            SystemClock.sleep(EDGE_PULSE_MILLIS)
            pulse += 1
            reachedDestination = destinationReached()
        }
        if (reachedDestination) repeat(DESTINATION_SETTLE_PULSES) { index ->
            val edgePoint = Point(to.x, to.y + if (index % 2 == 0) 1 else -1)
            automation.injectTouch(
                downTime = downTime,
                eventTime = SystemClock.uptimeMillis(),
                action = MotionEvent.ACTION_MOVE,
                point = edgePoint,
            )
            SystemClock.sleep(EDGE_PULSE_MILLIS)
        }

        automation.injectTouch(
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            action = MotionEvent.ACTION_UP,
            point = to,
        )
        pointerIsDown = false
        return reachedDestination
    } finally {
        if (pointerIsDown) {
            runCatching {
                automation.injectTouch(
                    downTime = downTime,
                    eventTime = SystemClock.uptimeMillis(),
                    action = MotionEvent.ACTION_CANCEL,
                    point = to,
                )
            }
        }
    }
}

private const val EDGE_PULSE_MILLIS = 16L
private const val EDGE_TIMEOUT_MILLIS = 10_000L
private const val DESTINATION_SETTLE_PULSES = 15

private fun UiAutomation.injectTouch(
    downTime: Long,
    eventTime: Long,
    action: Int,
    point: Point,
) {
    val event = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        point.x.toFloat(),
        point.y.toFloat(),
        0,
    ).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
    }
    try {
        check(injectInputEvent(event, true)) {
            "Unable to inject touch event for the habit reorder benchmark"
        }
    } finally {
        event.recycle()
    }
}
