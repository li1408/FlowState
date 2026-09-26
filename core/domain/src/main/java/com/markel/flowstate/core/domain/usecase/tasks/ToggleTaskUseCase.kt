package com.markel.flowstate.core.domain.usecase.tasks

import com.markel.flowstate.core.domain.Task
import javax.inject.Inject

class ToggleTaskUseCase @Inject constructor(
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
) {
    suspend operator fun invoke(task: Task) {
        if (task.isDone) {
            reopenTask(task.id)
        } else {
            completeTask(taskId = task.id, note = null, photoId = null)
        }
    }
}
