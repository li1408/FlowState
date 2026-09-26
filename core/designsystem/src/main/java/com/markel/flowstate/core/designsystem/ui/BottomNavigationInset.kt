package com.markel.flowstate.core.designsystem.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space reserved by the floating bottom navigation for scrollable content and FABs. */
val LocalBottomNavigationInset = staticCompositionLocalOf<Dp> { 0.dp }
