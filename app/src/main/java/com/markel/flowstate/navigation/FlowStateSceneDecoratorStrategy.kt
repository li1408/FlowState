package com.markel.flowstate.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.kyant.backdrop.Backdrop
import com.markel.flowstate.components.feedback.CompletionCelebrationOverlay
import com.markel.flowstate.components.feedback.COMPLETION_CELEBRATION_DURATION_MILLIS
import com.markel.flowstate.components.liquidglass.LiquidBottomBarMotion
import com.markel.flowstate.components.liquidglass.ScreenBackdrop
import com.markel.flowstate.components.liquidglass.screenBackdrop
import com.markel.flowstate.core.designsystem.feedback.LocalCompletionCelebrationHostState
import com.markel.flowstate.core.designsystem.ui.LocalBottomNavigationInset

/**
 * Keeps the bar inside top-level navigation scenes so fullscreen and
 * predictive-back transitions preserve their correct draw order. The
 * [backdrop] itself survives every tab switch. Only the active page-content
 * branch records into it; the bar is a sibling consumer so the captured layer
 * can never contain a RenderNode that reads the same layer.
 */
internal class FlowStateSceneDecoratorStrategy(
    private val sharedTransitionScope: SharedTransitionScope,
    private val backdrop: ScreenBackdrop,
    private val activeTopLevelRoute: () -> NavKey,
    private val bottomBarContent: @Composable (Backdrop) -> Unit,
    private val celebrationContent: @Composable (Backdrop) -> Unit,
) : SceneDecoratorStrategy<NavKey> {

    override fun SceneDecoratorStrategyScope<NavKey>.decorateScene(
        scene: Scene<NavKey>,
    ): Scene<NavKey> {
        if (scene.metadata.get<Boolean>(FullScreenMeta) == true) return scene
        return FlowStateDecoratedScene(
            scene = scene,
            sceneTopLevelRoute = scene.metadata.get(TopLevelRouteMeta),
            sharedTransitionScope = sharedTransitionScope,
            backdrop = backdrop,
            activeTopLevelRoute = activeTopLevelRoute,
            bottomBarContent = bottomBarContent,
            celebrationContent = celebrationContent,
        )
    }
}

@Composable
internal fun rememberFlowStateSceneDecoratorStrategy(
    sharedTransitionScope: SharedTransitionScope,
    backdrop: ScreenBackdrop,
    activeTopLevelRoute: NavKey,
    bottomBar: @Composable (Backdrop) -> Unit,
): FlowStateSceneDecoratorStrategy {
    val latestBottomBar = rememberUpdatedState(bottomBar)
    val latestActiveTopLevelRoute = rememberUpdatedState(activeTopLevelRoute)
    val movableBottomBar = remember {
        movableContentOf<Backdrop> { source -> latestBottomBar.value(source) }
    }
    val movableCelebration = remember {
        movableContentOf<Backdrop> { source ->
            val hostState = LocalCompletionCelebrationHostState.current
            val event = hostState.currentEvent
            if (event != null) {
                val playEntranceAnimation = remember(event.eventId) {
                    hostState.shouldPlayEntranceAnimation(event.eventId)
                }
                val remainingDurationMillis = remember(event.eventId) {
                    hostState.remainingDurationMillis(
                        eventId = event.eventId,
                        totalDurationMillis = COMPLETION_CELEBRATION_DURATION_MILLIS,
                    )
                }
                CompletionCelebrationOverlay(
                    event = event,
                    backdrop = source,
                    playEntranceAnimation = playEntranceAnimation,
                    remainingDurationMillis = remainingDurationMillis,
                    onEntranceAnimationStarted = {
                        hostState.claimEntranceAnimation(event.eventId)
                    },
                )
            }
        }
    }
    return remember(sharedTransitionScope, backdrop, movableBottomBar, movableCelebration) {
        FlowStateSceneDecoratorStrategy(
            sharedTransitionScope = sharedTransitionScope,
            backdrop = backdrop,
            activeTopLevelRoute = { latestActiveTopLevelRoute.value },
            bottomBarContent = movableBottomBar,
            celebrationContent = movableCelebration,
        )
    }
}

private class FlowStateDecoratedScene(
    private val scene: Scene<NavKey>,
    private val sceneTopLevelRoute: NavKey?,
    private val sharedTransitionScope: SharedTransitionScope,
    private val backdrop: ScreenBackdrop,
    private val activeTopLevelRoute: () -> NavKey,
    private val bottomBarContent: @Composable (Backdrop) -> Unit,
    private val celebrationContent: @Composable (Backdrop) -> Unit,
) : Scene<NavKey> by scene {

    override val key: Any = "flowstate_decorated" to scene.key

    override val content: @Composable () -> Unit = @Composable {
        val animatedContentScope = LocalNavAnimatedContentScope.current
        val isBottomBarOwner = sceneTopLevelRoute == activeTopLevelRoute()
        val density = LocalDensity.current
        val bottomContentInset = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp() +
                LiquidBottomBarMotion.ContentClearanceDp.dp
        }

        with(sharedTransitionScope) {
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalBottomNavigationInset provides bottomContentInset,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isBottomBarOwner) Modifier.screenBackdrop(backdrop)
                                else Modifier,
                            )
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        scene.content()
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .cacheSize(!isBottomBarOwner)
                        .sharedElement(
                            rememberSharedContentState("flowstate-bottom-bar"),
                            animatedContentScope,
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (isBottomBarOwner) bottomBarContent(backdrop)
                }

                // Like the bottom bar, the overlay moves between owner scenes
                // without restarting its Animatable or Konfetti frame loop.
                if (isBottomBarOwner) celebrationContent(backdrop)
            }
        }
    }
}
