package com.markel.flowstate.components.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.markel.flowstate.R
import com.markel.flowstate.core.designsystem.feedback.CompletionCelebrationEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

/**
 * A pointer-transparent sibling of the screen recorder. It samples the stable
 * navigation backdrop without ever becoming one of its recorded descendants.
 */
@Composable
internal fun CompletionCelebrationOverlay(
    event: CompletionCelebrationEvent,
    backdrop: Backdrop,
    playEntranceAnimation: Boolean,
    remainingDurationMillis: Long,
    onEntranceAnimationStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember(event.eventId) {
        Animatable(
            if (playEntranceAnimation) 0f
            else restoredAlpha(remainingDurationMillis),
        )
    }
    val scale = remember(event.eventId) {
        Animatable(if (playEntranceAnimation) 0.72f else 1f)
    }
    val primary = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val progressText = if (event.remainingCount == 0) {
        stringResource(R.string.completion_celebration_all_done, event.totalCount)
    } else {
        stringResource(
            R.string.completion_celebration_progress,
            event.completedCount,
            event.totalCount,
            event.remainingCount,
        )
    }
    val announcement = stringResource(
        R.string.completion_celebration_announcement,
        event.taskTitle,
        progressText,
    )
    val parties = remember(event.eventId) {
        listOf(
            Party(
                speed = 12f,
                maxSpeed = 34f,
                damping = 0.90f,
                angle = 270,
                spread = 110,
                colors = listOf(
                    0xFF16A269.toInt(),
                    0xFF58D6C7.toInt(),
                    0xFFFFC857.toInt(),
                    0xFFFF7A70.toInt(),
                ),
                timeToLive = 1_400L,
                position = Position.Relative(0.5, 0.46),
                emitter = Emitter(120, TimeUnit.MILLISECONDS).max(76),
            ),
        )
    }

    LaunchedEffect(event.eventId) {
        if (playEntranceAnimation) {
            onEntranceAnimationStarted()
            coroutineScope {
                launch {
                    alpha.animateTo(1f, tween(durationMillis = ENTRANCE_FADE_MILLIS))
                    delay(HOLD_AFTER_ENTRANCE_MILLIS)
                    alpha.animateTo(0f, tween(durationMillis = EXIT_FADE_MILLIS))
                }
                launch {
                    scale.animateTo(
                        targetValue = 1.06f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
            }
        } else {
            val fadeMillis = (remainingDurationMillis - INVISIBLE_TAIL_MILLIS)
                .coerceIn(0L, EXIT_FADE_MILLIS.toLong())
            val holdMillis = (remainingDurationMillis - INVISIBLE_TAIL_MILLIS - fadeMillis)
                .coerceAtLeast(0L)
            delay(holdMillis)
            if (fadeMillis > 0L) {
                alpha.animateTo(0f, tween(durationMillis = fadeMillis.toInt()))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = announcement
            },
    ) {
        if (playEntranceAnimation) {
            key(event.eventId) {
                KonfettiView(
                    modifier = Modifier.fillMaxSize(),
                    parties = parties,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth(0.88f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(
                            refractionHeight = 15.dp.toPx(),
                            refractionAmount = 18.dp.toPx(),
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.78f) },
                    shadow = { Shadow(alpha = 0.32f) },
                    innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.42f) },
                    onDrawSurface = { drawRect(surfaceColor) },
                )
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CompletionCheckmark(color = primary)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.completion_celebration_title),
                color = primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = event.taskTitle,
                modifier = Modifier.padding(top = 4.dp),
                color = onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = progressText,
                modifier = Modifier.padding(top = 6.dp),
                color = supportingColor,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val ENTRANCE_FADE_MILLIS = 140
private const val HOLD_AFTER_ENTRANCE_MILLIS = 1_050L
private const val EXIT_FADE_MILLIS = 460
private const val INVISIBLE_TAIL_MILLIS = 150L

private fun restoredAlpha(remainingDurationMillis: Long): Float {
    val fadeMillis = (remainingDurationMillis - INVISIBLE_TAIL_MILLIS)
        .coerceIn(0L, EXIT_FADE_MILLIS.toLong())
    return fadeMillis.toFloat() / EXIT_FADE_MILLIS
}

@Composable
private fun CompletionCheckmark(color: Color) {
    val circleColor = color.copy(alpha = 0.16f)
    Canvas(modifier = Modifier.size(56.dp)) {
        drawCircle(circleColor)
        val strokeWidth = 4.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.27f, size.height * 0.52f),
            end = Offset(size.width * 0.44f, size.height * 0.69f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.44f, size.height * 0.69f),
            end = Offset(size.width * 0.75f, size.height * 0.35f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
