package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markel.flowstate.feature.tasks.R

/**
 * Expandable FAB menu built on top of the native Material 3 Expressive
 * [FloatingActionButtonMenu] + [ToggleFloatingActionButton].
 *
 * Native behavior:
 *  - Container morphs squircle -> circle as the toggle is checked.
 *  - Container color morphs primaryContainer -> primary.
 *  - The bouncy spring is provided by the M3 Expressive
 *    MotionScheme, which is only active when the app is wrapped in
 *    MaterialExpressiveTheme (see Theme.kt).
 *
 * Scroll-to-hide:
 *  - Following the example in the official Android docs for
 *    [ToggleFloatingActionButton] and [FloatingActionButtonMenu], the
 *    [androidx.compose.material3.animateFloatingActionButton] modifier is
 *    applied to the [ToggleFloatingActionButton] ONLY (inside the `button`
 *    slot), NEVER to the [FloatingActionButtonMenu] itself.
 *
 *    Applying it to the whole menu multiplies the parent's scale+alpha with
 *    the per-item staggered scale and the toggle's morph, producing visual
 *    glitches. Applied to the toggle only, it is purely additive — the morph
 *    happens at the layout level (Dp) and the scroll scale happens on top.
 *
 *  - [visible] is OR'd with [expanded] before reaching the modifier so that
 *    the toggle never hides while the menu is open (otherwise the menu items
 *    would be left floating with no way to close them).
 *
 *  - The `modifier` parameter of this composable is forwarded to the
 *    [FloatingActionButtonMenu] and should only carry positioning
 *    (e.g. `Modifier.align(Alignment.BottomEnd)`), never visibility animation.
 *
 * @param expanded   Whether the menu is open (drives the toggle + item stagger).
 * @param visible    Whether the toggle should be visible (typically derived
 *                   from scroll state). Defaults to `true` for callers that
 *                   don't need scroll-to-hide. See FlowScreen.kt for an example.
 * @param modifier   Positioning modifier for the menu (NOT visibility animation).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onTaskClick: () -> Unit,
    onIdeaClick: () -> Unit,
    onCheckListClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val addIcon = ImageVector.vectorResource(R.drawable.add_24px)
    val closeIcon = ImageVector.vectorResource(R.drawable.close_24px)

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { onToggle() },
                modifier = Modifier.animateFloatingActionButton(
                    visible = visible || expanded,
                    alignment = Alignment.BottomEnd,
                ),
                containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium()
            ) {
                val progress = checkedProgress

                val iconTint = lerp(
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.onPrimary,
                    progress
                )

                Icon(
                    imageVector = if (progress < 0.5f) addIcon else closeIcon,
                    contentDescription = if (expanded) {
                        stringResource(R.string.access_close_menu)
                    } else {
                        stringResource(R.string.access_open_menu)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = onCheckListClick,
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.check_box_24px),
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.checklist), fontSize = 16.sp) }
        )
        FloatingActionButtonMenuItem(
            onClick = onIdeaClick,
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.lightbulb_24px),
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.idea), fontSize = 16.sp) }
        )
        FloatingActionButtonMenuItem(
            onClick = onTaskClick,
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.check_24px),
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.task), fontSize = 16.sp) }
        )
    }
}
