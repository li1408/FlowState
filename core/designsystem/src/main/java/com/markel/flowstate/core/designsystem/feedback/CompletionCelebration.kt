package com.markel.flowstate.core.designsystem.feedback

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A single, already-persisted task completion that should receive transient
 * visual, haptic and audio feedback.
 *
 * [eventId] must identify this completion attempt, rather than the task. That
 * lets a delayed dismissal ignore a newer completion of the same task.
 */
@Immutable
data class CompletionCelebrationEvent(
    val eventId: Long,
    val taskTitle: String,
    val completedCount: Int,
    val totalCount: Int,
) {
    val remainingCount: Int
        get() = (totalCount - completedCount).coerceAtLeast(0)
}

/**
 * Navigation-scoped owner for the one celebration that may be visible.
 *
 * The application owns this holder in its activity ViewModel, so a
 * configuration change neither replays acknowledged side effects nor extends
 * the event lifetime. Process recreation creates a fresh holder and does not
 * restore a stale celebration.
 */
@Stable
class CompletionCelebrationHostState private constructor(
    private val acceptsEvents: Boolean,
    private val monotonicNanos: () -> Long,
) {
    constructor() : this(
        acceptsEvents = true,
        monotonicNanos = System::nanoTime,
    )

    internal constructor(monotonicNanos: () -> Long) : this(
        acceptsEvents = true,
        monotonicNanos = monotonicNanos,
    )

    private var feedbackEventId: Long? = null
    private var visualEventId: Long? = null
    private var shownAtNanos: Long = 0L

    var currentEvent: CompletionCelebrationEvent? by mutableStateOf(null)
        private set

    fun show(event: CompletionCelebrationEvent) {
        if (!acceptsEvents) return
        if (currentEvent?.eventId != event.eventId) {
            shownAtNanos = monotonicNanos()
        }
        currentEvent = event
    }

    /** Clears only the event whose lifetime actually elapsed. */
    fun dismiss(eventId: Long) {
        if (currentEvent?.eventId == eventId) {
            currentEvent = null
            if (feedbackEventId == eventId) feedbackEventId = null
            if (visualEventId == eventId) visualEventId = null
        }
    }

    /**
     * Claims the one-shot sound and haptic for [eventId]. Visual hosts may be
     * recreated during navigation without replaying side effects.
     */
    fun claimFeedback(eventId: Long): Boolean {
        if (!acceptsEvents || currentEvent?.eventId != eventId || feedbackEventId == eventId) {
            return false
        }
        feedbackEventId = eventId
        return true
    }

    fun shouldPlayEntranceAnimation(eventId: Long): Boolean =
        currentEvent?.eventId == eventId && visualEventId != eventId

    fun claimEntranceAnimation(eventId: Long) {
        if (currentEvent?.eventId == eventId) visualEventId = eventId
    }

    /** Remaining lifetime without resetting it when the host is recreated. */
    fun remainingDurationMillis(eventId: Long, totalDurationMillis: Long): Long {
        if (currentEvent?.eventId != eventId) return 0L
        val elapsedNanos = (monotonicNanos() - shownAtNanos).coerceAtLeast(0L)
        val elapsedMillis = elapsedNanos / NANOS_PER_MILLISECOND
        return (totalDurationMillis - elapsedMillis).coerceAtLeast(0L)
    }

    internal companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        fun disabled(): CompletionCelebrationHostState =
            CompletionCelebrationHostState(
                acceptsEvents = false,
                monotonicNanos = System::nanoTime,
            )
    }
}

/**
 * Safe no-op outside the application host so previews and isolated feature
 * tests do not need to install an artificial navigation tree.
 */
val LocalCompletionCelebrationHostState = staticCompositionLocalOf {
    CompletionCelebrationHostState.disabled()
}
