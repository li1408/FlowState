package com.markel.flowstate.feature.flow.completion

import com.markel.flowstate.core.data.CompletionPhotoDraft
import com.markel.flowstate.core.domain.Task

sealed interface TaskCompletionUiState {
    data object Idle : TaskCompletionUiState

    data class Draft(
        val task: Task,
        val note: String = "",
        val photo: CompletionPhotoDraft? = null,
        val pendingCamera: CompletionPhotoDraft? = null,
        val isImporting: Boolean = false,
        val isSubmitting: Boolean = false,
        val error: TaskCompletionError? = null,
    ) : TaskCompletionUiState

    data class Celebrating(
        val eventId: Long,
        val task: Task,
        val completedCount: Int,
        val totalCount: Int,
    ) : TaskCompletionUiState {
        val remainingCount: Int = (totalCount - completedCount).coerceAtLeast(0)
    }
}

enum class TaskCompletionError {
    PhotoImportFailed,
    SaveFailed,
    TaskUnavailable,
    AlreadyCompleted,
}
