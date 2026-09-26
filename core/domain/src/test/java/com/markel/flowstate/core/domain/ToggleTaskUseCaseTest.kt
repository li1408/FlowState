package com.markel.flowstate.core.domain

import com.markel.flowstate.core.domain.usecase.tasks.ToggleTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.CompleteTaskUseCase
import com.markel.flowstate.core.domain.usecase.tasks.ReopenTaskUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleTaskUseCaseTest {

    private val completeTaskUseCase: CompleteTaskUseCase = mockk(relaxed = true)
    private val reopenTaskUseCase: ReopenTaskUseCase = mockk(relaxed = true)
    private val useCase = ToggleTaskUseCase(completeTaskUseCase, reopenTaskUseCase)

    @Test
    fun invoke_pendingTask_commitsEmptyCompletionRecord() = runTest {
        val task = Task(
            id = 1, title = "Demo", isDone = false, completedAt = null,
            reminderTime = 12345L,
            subTasks = listOf(SubTask(id = "s1", title = "Sub", reminderTime = 9999L))
        )

        useCase(task)

        coVerify(exactly = 1) {
            completeTaskUseCase(taskId = task.id, note = null, photoId = null)
        }
        coVerify(exactly = 0) { reopenTaskUseCase(any()) }
    }

    // Just to check that marking a task as pending doesn't recover any reminder.
    // This is an intended behavior, after completing a task the reminder is "consumed".
    // The only edge case when you would like to recover the reminder is if you accidentally completed
    // a task, but most people if the "uncomplete" a task won't remember any reminder and would be
    // confusing to recover it.
    @Test
    fun invoke_completedTask_reopensWithoutRestoringReminders() = runTest {
        val task = Task(
            id = 1, title = "Demo", isDone = true, completedAt = 1000L,
            reminderTime = null,
            subTasks = listOf(SubTask(id = "s1", title = "Sub", reminderTime = null))
        )

        useCase(task)

        coVerify(exactly = 1) { reopenTaskUseCase(task.id) }
        coVerify(exactly = 0) { completeTaskUseCase(any(), any(), any()) }
    }
}
