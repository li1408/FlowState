package com.markel.flowstate.feature.flow.tasks.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskItemCompletionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkbox_hasAccessibleTouchTarget_andRequestsCompletionWithoutLocalStateMutation() {
        var completionRequests = 0

        composeRule.setContent {
            MaterialTheme {
                TaskItemContent(
                    taskId = 7,
            title = "完成一项挑战",
                    isDone = false,
                    shape = RoundedCornerShape(16.dp),
                    onClicked = {},
                    onCheckClicked = { completionRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("task_checkbox_7")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assertIsOff()
            .performClick()
            .assertIsOff()

        assertEquals(1, completionRequests)
    }

    @Test
    fun completedTask_checkboxReflectsPersistedState() {
        composeRule.setContent {
            MaterialTheme {
                TaskItemContent(
                    taskId = 9,
                    title = "完成后的任务",
                    isDone = true,
                    shape = RoundedCornerShape(16.dp),
                    onClicked = {},
                    onCheckClicked = {},
                )
            }
        }

        composeRule.onNodeWithTag("task_checkbox_9").assertIsOn()
    }
}
