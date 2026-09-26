package com.markel.flowstate.core.designsystem.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionCelebrationHostStateTest {

    @Test
    fun `stale dismiss cannot clear a newer celebration`() {
        val hostState = CompletionCelebrationHostState()
        val first = event(id = 1L, completedCount = 1)
        val second = event(id = 2L, completedCount = 2)

        hostState.show(first)
        hostState.show(second)
        hostState.dismiss(first.eventId)

        assertEquals(second, hostState.currentEvent)

        hostState.dismiss(second.eventId)
        assertNull(hostState.currentEvent)
    }

    @Test
    fun `remaining count is never negative`() {
        val event = event(id = 3L, completedCount = 31, totalCount = 30)

        assertEquals(0, event.remainingCount)
    }

    @Test
    fun `feedback side effects can only be claimed once per event`() {
        val hostState = CompletionCelebrationHostState()
        val event = event(id = 4L, completedCount = 4)

        hostState.show(event)

        assertEquals(true, hostState.claimFeedback(event.eventId))
        assertEquals(false, hostState.claimFeedback(event.eventId))
        assertEquals(false, hostState.claimFeedback(eventId = 999L))

        hostState.dismiss(event.eventId)
        hostState.show(event)
        assertEquals(true, hostState.claimFeedback(event.eventId))
    }

    @Test
    fun `showing the same event again does not extend its lifetime`() {
        var nowNanos = 10_000_000L
        val hostState = CompletionCelebrationHostState { nowNanos }
        val event = event(id = 5L, completedCount = 5)

        hostState.show(event)
        nowNanos += 700_000_000L
        hostState.show(event.copy(taskTitle = "Updated title"))

        assertEquals(1_100L, hostState.remainingDurationMillis(event.eventId, 1_800L))
    }

    @Test
    fun `entrance animation is only offered once while an event is active`() {
        val hostState = CompletionCelebrationHostState()
        val event = event(id = 6L, completedCount = 6)

        hostState.show(event)
        assertEquals(true, hostState.shouldPlayEntranceAnimation(event.eventId))

        hostState.claimEntranceAnimation(event.eventId)
        assertEquals(false, hostState.shouldPlayEntranceAnimation(event.eventId))
    }

    private fun event(
        id: Long,
        completedCount: Int,
        totalCount: Int = 30,
    ) = CompletionCelebrationEvent(
        eventId = id,
        taskTitle = "Task $id",
        completedCount = completedCount,
        totalCount = totalCount,
    )
}
