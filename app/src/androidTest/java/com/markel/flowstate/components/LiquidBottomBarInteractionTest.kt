package com.markel.flowstate.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.markel.flowstate.navigation.BottomNavScreen
import com.markel.flowstate.navigation.TabKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidBottomBarInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rapidClicks_commitOnlyTheLatestDestination_andKeepOneSemanticTab() {
        val commits = mutableListOf<NavKey>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                FlowBottomBar(
                    topLevelRoute = TabKey.Tasks,
                    onNavigate = { key, _ -> commits += key },
                    isLandscape = false,
                    backdrop = EmptyBackdrop,
                    items = listOf(
                        BottomNavScreen.Tasks,
                        BottomNavScreen.Calendar,
                        BottomNavScreen.Habits,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("benchmark_bottom_tab_calendar").performTouchInput { click() }
        // Let the first physical press move beyond the 40% travel gate while
        // remaining inside the latest-intent coalescing window.
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("benchmark_bottom_tab_habits").performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(listOf(TabKey.Habits), commits)
        }
        composeRule.onAllNodesWithTag("benchmark_bottom_tab_habits").assertCountEquals(1)
    }

    @Test
    fun committedRoute_isExposedAsTheSelectedTab() {
        composeRule.setContent {
            MaterialTheme {
                FlowBottomBar(
                    topLevelRoute = TabKey.Calendar,
                    onNavigate = { _, _ -> },
                    isLandscape = false,
                    backdrop = EmptyBackdrop,
                    items = listOf(BottomNavScreen.Tasks, BottomNavScreen.Calendar),
                )
            }
        }

        composeRule.onNodeWithTag("benchmark_bottom_tab_calendar").assertIsSelected()
    }

    private data object EmptyBackdrop : Backdrop {
        override val isCoordinatesDependent: Boolean = false

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
        ) = Unit
    }
}
