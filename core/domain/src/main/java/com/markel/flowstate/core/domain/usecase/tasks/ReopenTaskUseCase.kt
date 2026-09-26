package com.markel.flowstate.core.domain.usecase.tasks

import com.markel.flowstate.core.domain.ReopenTaskResult
import com.markel.flowstate.core.domain.TaskRepository
import javax.inject.Inject

class ReopenTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(taskId: Int): ReopenTaskResult = repository.reopenTask(taskId)
}
