package com.markel.flowstate.feature.flow.completion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.markel.flowstate.core.domain.Task
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCompletionSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyDraft_requiresRecordForPrimaryAction_butAllowsSkip() {
        var submitted = 0
        var skipped = 0
        var draft by mutableStateOf(TaskCompletionUiState.Draft(task = testTask()))

        composeRule.setContent {
            MaterialTheme {
                TaskCompletionSheet(
                    state = draft,
                    onNoteChange = { draft = draft.copy(note = it) },
                    onTakePhoto = {},
                    onChoosePhoto = {},
                    onRemovePhoto = {},
                    onSubmit = { submitted += 1 },
                    onSkip = { skipped += 1 },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("completion_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("completion_skip").assertIsEnabled().performClick()
        assertEquals(1, skipped)
        assertEquals(0, submitted)

        composeRule.onNodeWithTag("completion_note").performTextInput("今天终于完成了")
        composeRule.onNodeWithTag("completion_submit").assertIsEnabled().performClick()
        assertEquals(1, submitted)
    }

    @Test
    fun mediaActions_areExposedAndIndependent() {
        var cameraRequests = 0
        var galleryRequests = 0

        composeRule.setContent {
            MaterialTheme {
                TaskCompletionSheet(
                    state = TaskCompletionUiState.Draft(task = testTask()),
                    onNoteChange = {},
                    onTakePhoto = { cameraRequests += 1 },
                    onChoosePhoto = { galleryRequests += 1 },
                    onRemovePhoto = {},
                    onSubmit = {},
                    onSkip = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("completion_camera").performClick()
        composeRule.onNodeWithTag("completion_gallery").performClick()

        assertEquals(1, cameraRequests)
        assertEquals(1, galleryRequests)
    }

    private fun testTask() = Task(
        id = 42,
        title = "给一年后的自己写一封信",
        isDone = false,
    )
}
