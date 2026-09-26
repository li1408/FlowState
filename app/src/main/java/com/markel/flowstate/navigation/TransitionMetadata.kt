package com.markel.flowstate.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay

// ─────────────────────────────────────────────────────────────────────────────
// Transition metadata helpers
//
// In nav3, per-entry transitions are declared via `metadata` on each entry.
// Each helper covers the three transition keys required for full predictive
// back support:
//   - TransitionKey : forward navigation (push)
//   - PopTransitionKey : back navigation (pop)
//   - PredictivePopTransitionKey : swipe-from-edge (predictive back gesture)
//
// ─────────────────────────────────────────────────────────────────────────────

private const val FADE_MS = 220
private const val SLIDE_MS = 280
internal object TopLevelRouteMeta : NavMetadataKey<NavKey>

/**
 * Fade in/out — used for tab switches within the decorated scene.
 * The bottom bar stays put; only the inner content cross-fades.
 */
fun fadeTransition(durationMillis: Int = FADE_MS) = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(durationMillis)) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(animationSpec = tween(durationMillis))
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(animationSpec = tween(durationMillis))
    }
}

/**
 * Slide up from the bottom third + fade in.
 * Used for fullscreen destinations that must cover the bottom bar.
 *
 * Combines:
 *   - `FullScreenMeta = true` so the Scene Decorator skips wrapping this
 *     scene with the bar (it renders edge-to-edge over the bar).
 *   - Slide-up + fade-in on push.
 *   - Slide-down + fade-out on pop / predictive back.
 *
 */
fun fullScreenVerticalSlide(durationMillis: Int = SLIDE_MS) = metadata {
    // Mark as fullscreen so the decorator passes this scene through unwrapped.
    put(FullScreenMeta, true)

    // Push: slide up from the bottom third + fade in.
    put(NavDisplay.TransitionKey) {
        slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(durationMillis)
        ) + fadeIn(tween(durationMillis - 50, delayMillis = 30)) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    // Pop: slide down + fade out. Previous scene just appears underneath.
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith
            slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(durationMillis - 40)
            ) + fadeOut(tween(durationMillis - 80))
    }
    // Predictive back (swipe-from-edge): same as pop.
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith
            slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(durationMillis - 40)
            ) + fadeOut(tween(durationMillis - 80))
    }
}

/**
 * Fullscreen + container-transform (shared bounds).
 * KeepUntilTransitionsFinished entering and popping.
 */
fun fullScreenSharedBounds(durationMillis: Int = 250) = metadata {
    put(FullScreenMeta, true)

    put(NavDisplay.TransitionKey) {
        fadeIn(tween(durationMillis)) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(tween(durationMillis))
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(tween(durationMillis))
    }
}

/**
 * Cross-fades top-level pages while moving them a restrained 10 dp in the
 * same visual direction as the liquid selection lens.
 */
fun topLevelTabTransition(
    visualDirection: () -> Int,
    distancePx: Int,
    durationMillis: Int = FADE_MS,
    tabKey: TabKey? = null,
) = metadata {
    if (tabKey != null) put(TopLevelRouteMeta, tabKey)
    put(NavDisplay.TransitionKey) {
        val direction = visualDirection().coerceIn(-1, 1)
        (fadeIn(animationSpec = tween(durationMillis)) +
            slideInHorizontally(
                initialOffsetX = { direction * distancePx },
                animationSpec = tween(durationMillis),
            )) togetherWith
            (fadeOut(animationSpec = tween(durationMillis)) +
                slideOutHorizontally(
                    targetOffsetX = { -direction * distancePx },
                    animationSpec = tween(durationMillis),
                ))
    }
    put(NavDisplay.PopTransitionKey) {
        val direction = visualDirection().coerceIn(-1, 1)
        (fadeIn(animationSpec = tween(durationMillis)) +
            slideInHorizontally(
                initialOffsetX = { -direction * distancePx },
                animationSpec = tween(durationMillis),
            )) togetherWith
            (fadeOut(animationSpec = tween(durationMillis)) +
                slideOutHorizontally(
                    targetOffsetX = { direction * distancePx },
                    animationSpec = tween(durationMillis),
                ))
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(animationSpec = tween(durationMillis))
    }
}
