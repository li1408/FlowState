package com.markel.flowstate.core.domain

/** A durable record of the moment a task was completed. */
data class TaskCompletion(
    val taskId: Int,
    val note: String? = null,
    val photoId: String? = null,
    val completedAt: Long,
)

/** Result of the idempotent task-completion transaction. */
enum class CompletionCommitStatus {
    Completed,
    AlreadyCompleted,
    NotFound,
}

/** Result of moving a completed task back to the pending list. */
data class ReopenTaskResult(
    val reopened: Boolean,
    /** Photo retained with the recoverable completion record, if present. */
    val photoId: String? = null,
)
