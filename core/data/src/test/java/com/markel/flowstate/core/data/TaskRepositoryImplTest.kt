package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.TaskDao
import com.markel.flowstate.core.data.local.TaskCompletionRecordEntity
import com.markel.flowstate.core.domain.CompletionCommitStatus
import com.markel.flowstate.core.domain.Task
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryImplTest {

    private val dao: TaskDao = mockk(relaxed = true)
    private val photoStore: TaskCompletionPhotoStore = mockk(relaxed = true)
    private val repository = TaskRepositoryImpl(dao, photoStore)

    @Test
    fun completingReopenedTask_deletesOnlyTheReplacedPhotoAfterCommit() = runTest {
        coEvery { dao.getCompletionRecord(7) } returns completionRecord(OLD_PHOTO)
        coEvery { dao.commitTaskCompletion(any()) } returns CompletionCommitStatus.Completed

        val result = repository.completeTask(7, 123L, "new", NEW_PHOTO)

        assertEquals(CompletionCommitStatus.Completed, result)
        coVerify(exactly = 1) { photoStore.deletePhoto(OLD_PHOTO) }
        coVerify(exactly = 0) { photoStore.deletePhoto(NEW_PHOTO) }
    }

    @Test
    fun failedOrDuplicateCompletion_neverDeletesTheExistingPhoto() = runTest {
        coEvery { dao.getCompletionRecord(7) } returns completionRecord(OLD_PHOTO)
        coEvery { dao.commitTaskCompletion(any()) } returns CompletionCommitStatus.AlreadyCompleted

        repository.completeTask(7, 123L, null, null)

        coVerify(exactly = 0) { photoStore.deletePhoto(any()) }
    }

    @Test
    fun reorder_updatesPositionsWithoutCallingWholeEntityUpdate() = runTest {
        val tasks = listOf(
            Task(id = 4, title = "four", position = 0, isDone = true),
            Task(id = 2, title = "two", position = 1, isDone = false),
        )

        repository.updateTasksOrder(tasks)

        coVerify(exactly = 1) {
            dao.updateTaskPositions(match { entities ->
                entities.map { it.id to it.position } == listOf(4 to 0, 2 to 1)
            })
        }
    }

    @Test
    fun delete_usesPhotoResolvedInsideTheDatabaseTransaction() = runTest {
        coEvery { dao.deleteTaskAndReturnPhotoId(9) } returns CURRENT_PHOTO

        repository.deleteTask(Task(id = 9, title = "stale", isDone = false, position = 0))

        coVerify(exactly = 1) { photoStore.deletePhoto(CURRENT_PHOTO) }
    }

    companion object {
        private const val OLD_PHOTO = "11111111-1111-4111-8111-111111111111.jpg"
        private const val NEW_PHOTO = "22222222-2222-4222-8222-222222222222.jpg"
        private const val CURRENT_PHOTO = "33333333-3333-4333-8333-333333333333.jpg"
    }

    private fun completionRecord(photoId: String) = TaskCompletionRecordEntity(
        taskId = 7,
        note = "old",
        photoId = photoId,
        completedAt = 100L,
    )
}
