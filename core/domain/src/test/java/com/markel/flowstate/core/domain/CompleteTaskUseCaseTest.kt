package com.markel.flowstate.core.domain

import com.markel.flowstate.core.domain.usecase.tasks.CompleteTaskUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** RED contract tests for building and committing a task completion record. */
class CompleteTaskUseCaseTest {

    private val repository: TaskRepository = mockk()
    private val completedAt = 1_790_000_123_456L
    private val clock = Clock.fixed(Instant.ofEpochMilli(completedAt), ZoneOffset.UTC)
    private val useCase = CompleteTaskUseCase(repository, clock)

    @Test
    fun invoke_trimsNote_andCommitsTheClockTimestampAndPhotoId() = runTest {
        coEvery {
            repository.completeTask(
                taskId = 7,
                completedAt = completedAt,
                note = "完成了人生第一次独自观影",
                photoId = "55555555-5555-5555-5555-555555555555.jpg",
            )
        } returns CompletionCommitStatus.Completed

        val result = useCase(
            taskId = 7,
            note = "  完成了人生第一次独自观影  ",
            photoId = "55555555-5555-5555-5555-555555555555.jpg",
        )

        assertEquals(CompletionCommitStatus.Completed, result)
        coVerify(exactly = 1) {
            repository.completeTask(
                taskId = 7,
                completedAt = completedAt,
                note = "完成了人生第一次独自观影",
                photoId = "55555555-5555-5555-5555-555555555555.jpg",
            )
        }
    }

    @Test
    fun invoke_convertsBlankNoteToNull() = runTest {
        coEvery {
            repository.completeTask(
                taskId = 8,
                completedAt = completedAt,
                note = null,
                photoId = null,
            )
        } returns CompletionCommitStatus.Completed

        val result = useCase(taskId = 8, note = " \n\t ", photoId = null)

        assertEquals(CompletionCommitStatus.Completed, result)
        coVerify(exactly = 1) {
            repository.completeTask(
                taskId = 8,
                completedAt = completedAt,
                note = null,
                photoId = null,
            )
        }
    }

    @Test
    fun invoke_propagatesAlreadyCompletedWithoutRetryingOrOverwriting() = runTest {
        coEvery {
            repository.completeTask(
                taskId = 9,
                completedAt = completedAt,
                note = "第二次提交",
                photoId = null,
            )
        } returns CompletionCommitStatus.AlreadyCompleted

        val result = useCase(taskId = 9, note = "第二次提交", photoId = null)

        assertEquals(CompletionCommitStatus.AlreadyCompleted, result)
        coVerify(exactly = 1) {
            repository.completeTask(
                taskId = 9,
                completedAt = completedAt,
                note = "第二次提交",
                photoId = null,
            )
        }
    }

    @Test
    fun invoke_propagatesNotFound() = runTest {
        coEvery {
            repository.completeTask(
                taskId = 404,
                completedAt = completedAt,
                note = null,
                photoId = null,
            )
        } returns CompletionCommitStatus.NotFound

        val result = useCase(taskId = 404, note = null, photoId = null)

        assertEquals(CompletionCommitStatus.NotFound, result)
    }
}
