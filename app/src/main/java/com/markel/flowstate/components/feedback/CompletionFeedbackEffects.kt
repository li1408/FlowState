package com.markel.flowstate.components.feedback

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import com.markel.flowstate.core.designsystem.feedback.CompletionCelebrationHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val COMPLETION_CELEBRATION_DURATION_MILLIS = 1_800L

/**
 * Activity-scoped one-shot effects and lifetime management. Keeping this host
 * outside navigation scenes prevents tab/full-screen transitions from
 * replaying sound or haptics, or cancelling the event cleanup timer.
 */
@Composable
internal fun CompletionFeedbackEffects(
    hostState: CompletionCelebrationHostState,
) {
    val view = LocalView.current
    val soundPlayer = rememberCompletionSoundPlayer()
    val event = hostState.currentEvent

    LaunchedEffect(soundPlayer) {
        soundPlayer.prepare()
    }

    LaunchedEffect(event?.eventId) {
        val activeEvent = event ?: return@LaunchedEffect
        var soundJob: kotlinx.coroutines.Job? = null
        try {
            if (hostState.claimFeedback(activeEvent.eventId)) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                soundJob = launch { soundPlayer.play(activeEvent.eventId) }
            }
            delay(
                hostState.remainingDurationMillis(
                    eventId = activeEvent.eventId,
                    totalDurationMillis = COMPLETION_CELEBRATION_DURATION_MILLIS,
                ),
            )
            hostState.dismiss(activeEvent.eventId)
        } finally {
            soundPlayer.cancel(activeEvent.eventId)
            soundJob?.cancel()
        }
    }
}
