package com.markel.flowstate.core.designsystem.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.designsystem.R

@Composable
fun AnimatedUndoFab(
    visible: Boolean,
    onUndoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onUndoClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = 16.dp, bottom = 64.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.undo_24px),
                contentDescription = stringResource(R.string.undo)
            )
        }
    }
}
