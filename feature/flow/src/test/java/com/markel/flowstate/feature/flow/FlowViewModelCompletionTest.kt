package com.markel.flowstate.feature.flow

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.markel.flowstate.core.data.CompletionPhotoDraft
import com.markel.flowstate.core.data.TaskCompletionPhotoStore
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.domain.CategoryRepository
import com.markel.flowstate.core.domain.CheckListRepository
import com.markel.flowstate.core.domain.CompletionCommitStatus
import com.markel.flowstate.core.domain.IdeaRepository
import com.markel.flowstate.core.domain.Task
import com.markel.flowstate.core.domain.TaskCompletion
import com.markel.flowstate.core.domain.TaskRepository
import com.markel.flowstate.core.domain.usecase.tasks.CompleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.DeleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.ReopenTaskUseCase
import com.markel.flowstate.core.notifications.ReminderScheduler
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import com.markel.flowstate.feature.flow.completion.TaskCompletionUiState
import com.markel.flowstate.feature.flow.completion.TaskCompletionError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Contract tests for the task-completion UI state machine.
 *
 * These tests intentionally precede the production implementation. A task stays pending until
 * [CompleteTaskUseCase] succeeds, then remains available to the UI in a 1.4 second celebration
 * state before the completion surface closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelCompletionTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val taskRepository: TaskRepository = mockk()
    private val ideaRepository: IdeaRepository = mockk()
    private val checkListRepository: CheckListRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val reminderScheduler: ReminderScheduler = mockk(relaxed = true)
    private val deleteTaskUseCase: DeleteTaskUseCase = mockk(relaxed = true)
    private val completeTaskUseCase: CompleteTaskUseCase = mockk()
    private val reopenTaskUseCase: ReopenTaskUseCase = mockk(relaxed = true)
    private val taskCompletionPhotoStore: TaskCompletionPhotoStore = mockk(relaxed = true)

    private val tasks = MutableStateFlow<List<Task>>(emptyList())
    private lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)

        every { taskRepository.getTasks() } returns tasks
        every { ideaRepository.getIdeas() } returns flowOf(emptyList())
        every { checkListRepository.getLists() } returns flowOf(emptyList())
        every { categoryRepository.getCategories() } returns flowOf(emptyList())
        every { userPreferencesRepository.categoriesEnabled } returns flowOf(false)
        every { userPreferencesRepository.lastCategoryId } returns flowOf(null)
        every { userPreferencesRepository.generalCategoryName } returns flowOf(null)
        every { reminderScheduler.canScheduleExactAlarms() } returns true
        coEvery { taskRepository.getTaskById(any()) } returns null
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
    }

    @Test
    fun requestCompletion_opensDraft_withoutWritingToDatabase() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.requestCompletion(task)

        val state = viewModel.completionUiState.value
        assertTrue(state is TaskCompletionUiState.Draft)
        state as TaskCompletionUiState.Draft
        assertSame(task, state.task)
        assertEquals("", state.note)
        assertFalse(state.isSubmitting)
        coVerify(exactly = 0) { completeTaskUseCase(any(), any(), any()) }
    }

    @Test
    fun dismissCompletion_cancelsDraft_withoutWritingToDatabase() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)

        viewModel.dismissCompletion()

        assertEquals(TaskCompletionUiState.Idle, viewModel.completionUiState.value)
        coVerify(exactly = 0) { completeTaskUseCase(any(), any(), any()) }
    }

    @Test
    fun submitCompletion_writesNoteAndNoPhoto_once() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        coEvery {
            completeTaskUseCase(task.id, "First solo sunset", null)
        } returns CompletionCommitStatus.Completed
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.updateCompletionNote("First solo sunset")

        viewModel.submitCompletion(includeRecord = true)
        runCurrent()

        coVerify(exactly = 1) {
            completeTaskUseCase(task.id, "First solo sunset", null)
        }
        assertTrue(viewModel.completionUiState.value is TaskCompletionUiState.Celebrating)
    }

    @Test
    fun submitCompletion_writesImportedPhotoId_withNote() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val sourceUri: Uri = mockk()
        val photoId = "123e4567-e89b-42d3-a456-426614174000.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        tasks.value = listOf(task)
        coEvery { taskCompletionPhotoStore.importFromUri(sourceUri) } returns photoDraft
        coEvery { taskCompletionPhotoStore.promote(photoDraft) } returns photoId
        coEvery {
            completeTaskUseCase(task.id, "A photo memory", photoId)
        } returns CompletionCommitStatus.Completed
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.updateCompletionNote("A photo memory")

        viewModel.importCompletionPhoto(sourceUri)
        runCurrent()
        val draft = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertSame(photoDraft, draft.photo)

        viewModel.submitCompletion(includeRecord = true)
        runCurrent()

        coVerify(exactly = 1) { taskCompletionPhotoStore.promote(photoDraft) }
        coVerify(exactly = 1) {
            completeTaskUseCase(task.id, "A photo memory", photoId)
        }
        assertTrue(viewModel.completionUiState.value is TaskCompletionUiState.Celebrating)
    }

    @Test
    fun submitCompletion_whileAlreadySubmitting_doesNotCommitTwice() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        val commitStarted = CompletableDeferred<Unit>()
        val allowCommitToFinish = CompletableDeferred<Unit>()
        coEvery { completeTaskUseCase(task.id, null, null) } coAnswers {
            commitStarted.complete(Unit)
            allowCommitToFinish.await()
            CompletionCommitStatus.Completed
        }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)

        viewModel.submitCompletion()
        runCurrent()
        assertTrue(commitStarted.isCompleted)
        val submitting = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertTrue(submitting.isSubmitting)

        viewModel.submitCompletion()
        runCurrent()
        allowCommitToFinish.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { completeTaskUseCase(task.id, null, null) }
    }

    @Test
    fun successfulCommit_celebratesForExactly1400Milliseconds() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        coEvery { completeTaskUseCase(task.id, null, null) } returns
            CompletionCommitStatus.Completed
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)

        viewModel.submitCompletion()
        runCurrent()

        assertTrue(viewModel.completionUiState.value is TaskCompletionUiState.Celebrating)
        advanceTimeBy(1_399L)
        runCurrent()
        assertTrue(viewModel.completionUiState.value is TaskCompletionUiState.Celebrating)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(TaskCompletionUiState.Idle, viewModel.completionUiState.value)
    }

    @Test
    fun reopenDuringCelebration_isIgnoredUntilFeedbackFinishes() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        coEvery { completeTaskUseCase(task.id, null, null) } returns
            CompletionCommitStatus.Completed
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.submitCompletion()
        runCurrent()

        viewModel.reopenTask(task.copy(isDone = true))
        runCurrent()

        coVerify(exactly = 0) { reopenTaskUseCase(any()) }
    }

    @Test
    fun successfulSeventhCommit_reportsSevenOfThirty_andTwentyThreeRemaining() =
        runTest(dispatcher) {
            val challenge = challengeTasks()
            val task = challenge.single { it.id == 7 }
            tasks.value = challenge
            coEvery { completeTaskUseCase(task.id, null, null) } coAnswers {
                tasks.value = tasks.value.map { current ->
                    if (current.id == task.id) current.copy(isDone = true) else current
                }
                CompletionCommitStatus.Completed
            }
            val viewModel = createViewModel()
            runCurrent()
            viewModel.requestCompletion(task)

            viewModel.submitCompletion()
            runCurrent()

            val state = viewModel.completionUiState.value
            assertTrue(state is TaskCompletionUiState.Celebrating)
            state as TaskCompletionUiState.Celebrating
            assertEquals(task.id, state.task.id)
            assertEquals(7, state.completedCount)
            assertEquals(30, state.totalCount)
            assertEquals(23, state.remainingCount)
        }

    @Test
    fun failedCommit_keepsDraftAndNote_availableForRetry() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        coEvery { completeTaskUseCase(task.id, "Keep this note", null) } throws
            IllegalStateException("database unavailable")
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.updateCompletionNote("Keep this note")

        viewModel.submitCompletion()
        runCurrent()

        val state = viewModel.completionUiState.value
        assertTrue(state is TaskCompletionUiState.Draft)
        state as TaskCompletionUiState.Draft
        assertSame(task, state.task)
        assertEquals("Keep this note", state.note)
        assertFalse(state.isSubmitting)
        coVerify(exactly = 1) { completeTaskUseCase(task.id, "Keep this note", null) }
    }

    @Test
    fun postCommitReadFailure_keepsCommittedPhoto_andStillCelebrates() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val photoId = "123e4567-e89b-42d3-a456-426614174000.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        val sourceUri: Uri = mockk()
        tasks.value = listOf(task)
        coEvery { taskCompletionPhotoStore.importFromUri(sourceUri) } returns photoDraft
        coEvery { taskCompletionPhotoStore.promote(photoDraft) } returns photoId
        coEvery { completeTaskUseCase(task.id, null, photoId) } returns
            CompletionCommitStatus.Completed
        coEvery { taskRepository.getTaskById(task.id) } throws
            IllegalStateException("post-commit refresh failed")
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.importCompletionPhoto(sourceUri)
        runCurrent()

        viewModel.submitCompletion(includeRecord = true)
        runCurrent()

        assertTrue(viewModel.completionUiState.value is TaskCompletionUiState.Celebrating)
        coVerify(exactly = 0) { taskCompletionPhotoStore.deletePhoto(photoId) }
    }

    @Test
    fun cancellationAfterDatabaseCommit_neverDeletesReferencedPhoto() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val photoId = "123e4567-e89b-42d3-a456-426614174000.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        val sourceUri: Uri = mockk()
        val completed = task.copy(
            isDone = true,
            completedAt = 123L,
            completion = TaskCompletion(task.id, null, photoId, 123L),
        )
        tasks.value = listOf(task)
        coEvery { taskCompletionPhotoStore.importFromUri(sourceUri) } returns photoDraft
        coEvery { taskCompletionPhotoStore.promote(photoDraft) } returns photoId
        coEvery { completeTaskUseCase(task.id, null, photoId) } throws
            CancellationException("cancelled after Room committed")
        coEvery { taskRepository.getTaskById(task.id) } returns completed
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.importCompletionPhoto(sourceUri)
        runCurrent()

        viewModel.submitCompletion(includeRecord = true)
        runCurrent()

        coVerify(exactly = 0) { taskCompletionPhotoStore.deletePhoto(photoId) }
    }

    @Test
    fun externallyCompletedTask_keepsDraftAndReportsConflict() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val photoId = "123e4567-e89b-42d3-a456-426614174000.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        val sourceUri: Uri = mockk()
        tasks.value = listOf(task)
        coEvery { taskCompletionPhotoStore.importFromUri(sourceUri) } returns photoDraft
        coEvery { taskCompletionPhotoStore.promote(photoDraft) } returns photoId
        coEvery { completeTaskUseCase(task.id, "Keep me", photoId) } returns
            CompletionCommitStatus.AlreadyCompleted
        val viewModel = createViewModel()
        runCurrent()
        viewModel.requestCompletion(task)
        viewModel.updateCompletionNote("Keep me")
        viewModel.importCompletionPhoto(sourceUri)
        runCurrent()

        viewModel.submitCompletion(includeRecord = true)
        runCurrent()

        val draft = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertSame(photoDraft, draft.photo)
        assertEquals("Keep me", draft.note)
        assertEquals(com.markel.flowstate.feature.flow.completion.TaskCompletionError.AlreadyCompleted, draft.error)
        coVerify(exactly = 1) { taskCompletionPhotoStore.deletePhoto(photoId) }
        coVerify(exactly = 0) { taskCompletionPhotoStore.deleteDraft(photoDraft) }
    }

    @Test
    fun savedDraft_restoresTaskNoteAndPhoto_afterProcessRecreation() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val photoId = "123e4567-e89b-42d3-a456-426614174001.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        val savedState = SavedStateHandle(
            mapOf(
                COMPLETION_TASK_ID to task.id,
                COMPLETION_NOTE to "Keep this memory",
                COMPLETION_PHOTO_ID to photoId,
            ),
        )
        coEvery { taskRepository.getTaskById(task.id) } returns task
        every { taskCompletionPhotoStore.restorePhotoDraft(photoId) } returns photoDraft

        val viewModel = createViewModel(savedState)
        runCurrent()

        val restored = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertEquals(task, restored.task)
        assertEquals("Keep this memory", restored.note)
        assertSame(photoDraft, restored.photo)
        assertFalse(restored.isImporting)
    }

    @Test
    fun dismissRestoredDraft_deletesAnyUncommittedPromotedCopy() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val photoId = "123e4567-e89b-42d3-a456-426614174005.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns photoId
        }
        val savedState = SavedStateHandle(
            mapOf(
                COMPLETION_TASK_ID to task.id,
                COMPLETION_NOTE to "Retryable",
                COMPLETION_PHOTO_ID to photoId,
            ),
        )
        coEvery { taskRepository.getTaskById(task.id) } returns task
        every { taskCompletionPhotoStore.restorePhotoDraft(photoId) } returns photoDraft
        val viewModel = createViewModel(savedState)
        runCurrent()

        viewModel.dismissCompletion()

        verify(exactly = 1) { taskCompletionPhotoStore.deleteDraft(photoDraft) }
        verify(exactly = 1) { taskCompletionPhotoStore.deletePhoto(photoId) }
        assertEquals(TaskCompletionUiState.Idle, viewModel.completionUiState.value)
    }

    @Test
    fun cameraResult_waitsForSingleRestoreJob_beforeNormalizing() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val cameraId = "123e4567-e89b-42d3-a456-426614174002.jpg"
        val cameraFile: java.io.File = mockk {
            every { length() } returns 0L
        }
        val cameraDraft: CompletionPhotoDraft = mockk(relaxed = true) {
            every { id } returns cameraId
            every { file } returns cameraFile
        }
        val taskRead = CompletableDeferred<Task?>()
        val savedState = SavedStateHandle(
            mapOf(
                COMPLETION_TASK_ID to task.id,
                COMPLETION_NOTE to "Camera note",
                COMPLETION_PENDING_CAMERA_ID to cameraId,
            ),
        )
        coEvery { taskRepository.getTaskById(task.id) } coAnswers { taskRead.await() }
        every { taskCompletionPhotoStore.restoreCameraDraft(cameraId) } returns cameraDraft
        coEvery { taskCompletionPhotoStore.normalizeCameraDraft(cameraDraft) } returns cameraDraft
        val viewModel = createViewModel(savedState)

        viewModel.onCameraCaptureResult(succeeded = true)
        runCurrent()
        coVerify(exactly = 0) { taskCompletionPhotoStore.normalizeCameraDraft(cameraDraft) }

        taskRead.complete(task)
        runCurrent()

        val restored = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertSame(cameraDraft, restored.photo)
        assertEquals("Camera note", restored.note)
        assertFalse(restored.isImporting)
        coVerify(exactly = 1) { taskCompletionPhotoStore.normalizeCameraDraft(cameraDraft) }
    }

    @Test
    fun missingSavedPhoto_keepsTextAndReportsImportFailure() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        val missingPhotoId = "123e4567-e89b-42d3-a456-426614174003.jpg"
        val savedState = SavedStateHandle(
            mapOf(
                COMPLETION_TASK_ID to task.id,
                COMPLETION_NOTE to "Do not lose this text",
                COMPLETION_PHOTO_ID to missingPhotoId,
            ),
        )
        coEvery { taskRepository.getTaskById(task.id) } returns task
        every { taskCompletionPhotoStore.restorePhotoDraft(missingPhotoId) } returns null

        val viewModel = createViewModel(savedState)
        runCurrent()

        val restored = viewModel.completionUiState.value as TaskCompletionUiState.Draft
        assertEquals("Do not lose this text", restored.note)
        assertNull(restored.photo)
        assertEquals(TaskCompletionError.PhotoImportFailed, restored.error)
        verify(exactly = 1) { taskCompletionPhotoStore.deletePhoto(missingPhotoId) }
    }

    @Test
    fun completedTaskDuringRecreation_discardsDraftAndClearsSavedState() = runTest(dispatcher) {
        val task = pendingTask(id = 7).copy(isDone = true, completedAt = 123L)
        val photoId = "123e4567-e89b-42d3-a456-426614174004.jpg"
        val photoDraft: CompletionPhotoDraft = mockk(relaxed = true)
        val savedState = SavedStateHandle(
            mapOf(
                COMPLETION_TASK_ID to task.id,
                COMPLETION_NOTE to "Already committed",
                COMPLETION_PHOTO_ID to photoId,
            ),
        )
        coEvery { taskRepository.getTaskById(task.id) } returns task
        every { taskCompletionPhotoStore.restorePhotoDraft(photoId) } returns photoDraft

        val viewModel = createViewModel(savedState)
        runCurrent()

        assertEquals(TaskCompletionUiState.Idle, viewModel.completionUiState.value)
        assertNull(savedState.get<Int>(COMPLETION_TASK_ID))
        assertNull(savedState.get<String>(COMPLETION_NOTE))
        assertNull(savedState.get<String>(COMPLETION_PHOTO_ID))
        verify(exactly = 1) { taskCompletionPhotoStore.deleteDraft(photoDraft) }
        verify(exactly = 0) { taskCompletionPhotoStore.deletePhoto(photoId) }
    }

    @Test
    fun dismissAndSuccessfulCommit_clearSavedDraftKeys() = runTest(dispatcher) {
        val task = pendingTask(id = 7)
        tasks.value = listOf(task)
        val dismissedState = SavedStateHandle()
        val dismissedViewModel = createViewModel(dismissedState)
        runCurrent()
        dismissedViewModel.requestCompletion(task)
        dismissedViewModel.updateCompletionNote("Temporary")
        assertEquals(task.id, dismissedState.get<Int>(COMPLETION_TASK_ID))
        assertEquals("Temporary", dismissedState.get<String>(COMPLETION_NOTE))
        dismissedViewModel.dismissCompletion()

        assertNull(dismissedState.get<Int>(COMPLETION_TASK_ID))
        assertNull(dismissedState.get<String>(COMPLETION_NOTE))

        val committedState = SavedStateHandle()
        coEvery { completeTaskUseCase(task.id, null, null) } returns CompletionCommitStatus.Completed
        val committedViewModel = createViewModel(committedState)
        runCurrent()
        committedViewModel.requestCompletion(task)
        committedViewModel.submitCompletion()
        runCurrent()

        assertTrue(committedViewModel.completionUiState.value is TaskCompletionUiState.Celebrating)
        assertNull(committedState.get<Int>(COMPLETION_TASK_ID))
        assertNull(committedState.get<String>(COMPLETION_NOTE))
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): FlowViewModel = FlowViewModel(
        taskRepository = taskRepository,
        ideaRepository = ideaRepository,
        checkListRepository = checkListRepository,
        categoryRepository = categoryRepository,
        userPreferencesRepository = userPreferencesRepository,
        reminderScheduler = reminderScheduler,
        deleteTaskUseCase = deleteTaskUseCase,
        completeTaskUseCase = completeTaskUseCase,
        reopenTaskUseCase = reopenTaskUseCase,
        completionPhotoStore = taskCompletionPhotoStore,
        applicationScope = applicationScope,
        savedStateHandle = savedStateHandle,
    )

    private fun pendingTask(id: Int): Task = Task(
        id = id,
        title = "Task $id",
        isDone = false,
        position = id - 1,
    )

    private fun challengeTasks(): List<Task> = (1..30).map { id ->
        Task(
            id = id,
            title = "Task $id",
            isDone = id <= 6,
            position = id - 1,
            completedAt = if (id <= 6) id.toLong() else null,
        )
    }

    private companion object {
        const val COMPLETION_TASK_ID = "completion.taskId"
        const val COMPLETION_NOTE = "completion.note"
        const val COMPLETION_PHOTO_ID = "completion.photoDraftId"
        const val COMPLETION_PENDING_CAMERA_ID = "completion.pendingCameraDraftId"
    }
}
