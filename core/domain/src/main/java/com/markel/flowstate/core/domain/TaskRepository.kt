package com.markel.flowstate.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * Defines the data operations for Tasks.
 * This interface lives in :core:domain and knows nothing about Room or APIs.
 * It is the contract that ViewModels will use.
 */
interface TaskRepository {

    /**
     * Gets a Flow of all tasks.
     * The UI will automatically update when this changes.
     */
    fun getTasks(): Flow<List<Task>>

    /**
     * Gets a single task by its ID.
     * Returns null if not found.
     */
    suspend fun getTaskById(id: Int): Task?

    /**
     * Inserts or updates a task.
     */
    suspend fun upsertTask(task: Task): Long

    /**
     * Deletes a task.
     */
    suspend fun deleteTask(task: Task)

    /**
     * Updates the order of the tasks.
     */
    suspend fun updateTasksOrder(tasks: List<Task>)

    /**
     * Clears the reminderTime of a task, marking its reminder as consumed.
     */
    suspend fun clearTaskReminder(taskId: Int)

    /**
     * Clears the reminderTime of a subtask, marking its reminder as consumed.
     */
    suspend fun clearSubTaskReminder(subTaskId: String)

    /**
     * Atomically completes a task and saves its optional check-in record.
     * Repeating the call never overwrites the first successful record.
     */
    suspend fun completeTask(
        taskId: Int,
        completedAt: Long,
        note: String?,
        photoId: String?,
    ): CompletionCommitStatus

    /**
     * Reopens a task while retaining its prior check-in as recoverable history.
     * The next successful completion replaces it atomically.
     */
    suspend fun reopenTask(taskId: Int): ReopenTaskResult
}
