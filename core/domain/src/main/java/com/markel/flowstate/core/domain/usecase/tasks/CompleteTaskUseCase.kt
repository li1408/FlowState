package com.markel.flowstate.core.domain.usecase.tasks

import com.markel.flowstate.core.domain.CompletionCommitStatus
import com.markel.flowstate.core.domain.TaskRepository
import java.time.Clock
import javax.inject.Inject

class CompleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        taskId: Int,
        note: String?,
        photoId: String?,
    ): CompletionCommitStatus = repository.completeTask(
        taskId = taskId,
        completedAt = clock.millis(),
        note = note?.trim()?.takeIf(String::isNotEmpty),
        photoId = photoId,
    )
}
