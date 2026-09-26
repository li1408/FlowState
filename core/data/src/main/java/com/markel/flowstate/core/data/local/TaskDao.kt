package com.markel.flowstate.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object)
 * This interface defines HOW we talk to the database.
 * Room will write the code for us thanks to these annotations.
 */
@Dao
interface TaskDao {

    // "Upsert" = "Update" or "Insert".
    // If the task already exists, it updates it. If it's new, it creates it.
    @Upsert
    suspend fun upsertTaskEntity(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksByTaskId(taskId: Int)

    @Delete
    suspend fun deleteTaskEntity(task: TaskEntity)

    /**
     * This function handles the complete logic of saving a task with its subtasks
     * Being @Transaction, if something fails, nothing is saved (total integrity)
     */
    @Transaction
    suspend fun upsertTaskWithSubTasks(task: TaskEntity, subTasks: List<SubTaskEntity>): Long {
        // 1. We save/update the parent.
        val rowId = upsertTaskEntity(task)

        // For existing tasks, upsertTaskEntity returns -1 on some Room versions
        // when nothing changed, so we fall back to task.id.
        val taskId = if (task.id == 0) rowId.toInt() else task.id

        // 2. We delete the old subtasks to avoid duplicates or "ghosts"
        deleteSubTasksByTaskId(taskId)

        // 3. We assign the task ID to the subtasks and insert them
        val subTasksWithId = subTasks.map { it.copy(taskId = taskId) }
        insertSubTasks(subTasksWithId)

        // Return the real task ID as Long so callers can schedule alarms.
        return taskId.toLong()

    }

    // Requests all ordered tasks
    // "Flow" means that if something changes in the table, the UI will automatically update.
    @Transaction // Necessary because Room makes 2 internal queries since the method returns a class that contains a field with @Relation
    @Query("SELECT * FROM tasks ORDER BY position ASC")
    fun getTasks(): Flow<List<TaskWithSubTasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskWithSubTasks?

    @Query("UPDATE tasks SET position = :position WHERE id = :taskId")
    suspend fun updateTaskPosition(taskId: Int, position: Int)

    /** Ordering must never write a stale UI copy of completion/reminder fields. */
    @Transaction
    suspend fun updateTaskPositions(tasks: List<TaskEntity>) {
        tasks.forEach { task -> updateTaskPosition(task.id, task.position) }
    }

    @Query("UPDATE tasks SET reminderTime = NULL WHERE id = :taskId")
    suspend fun clearTaskReminder(taskId: Int)

    @Query("UPDATE subtasks SET reminderTime = NULL WHERE id = :subTaskId")
    suspend fun clearSubTaskReminder(subTaskId: String)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskEntity(taskId: Int): TaskEntity?

    @Query(
        """
        UPDATE tasks
        SET isDone = 1, completedAt = :completedAt, reminderTime = NULL
        WHERE id = :taskId AND isDone = 0
        """
    )
    suspend fun markTaskCompleted(taskId: Int, completedAt: Long): Int

    @Query("UPDATE subtasks SET reminderTime = NULL WHERE taskId = :taskId")
    suspend fun clearSubTaskRemindersForTask(taskId: Int)

    @Upsert
    suspend fun upsertCompletionRecord(record: TaskCompletionRecordEntity)

    @Query("SELECT * FROM task_completion_records WHERE taskId = :taskId LIMIT 1")
    fun observeCompletion(taskId: Int): Flow<TaskCompletionRecordEntity?>

    @Query("SELECT * FROM task_completion_records WHERE taskId = :taskId LIMIT 1")
    suspend fun getCompletionRecord(taskId: Int): TaskCompletionRecordEntity?

    @Query("SELECT * FROM task_completion_records ORDER BY completedAt ASC")
    suspend fun getAllCompletionRecordsOnce(): List<TaskCompletionRecordEntity>

    @Query("DELETE FROM task_completion_records WHERE taskId = :taskId")
    suspend fun deleteCompletionRecord(taskId: Int)

    @Query(
        """
        UPDATE tasks
        SET isDone = 0, completedAt = NULL
        WHERE id = :taskId AND isDone = 1
        """
    )
    suspend fun markTaskPending(taskId: Int): Int

    @Transaction
    suspend fun commitTaskCompletion(
        record: TaskCompletionRecordEntity,
    ): CompletionCommitStatus {
        val task = getTaskEntity(record.taskId) ?: return CompletionCommitStatus.NotFound
        if (task.isDone) return CompletionCommitStatus.AlreadyCompleted

        val changed = markTaskCompleted(record.taskId, record.completedAt)
        if (changed == 0) return CompletionCommitStatus.AlreadyCompleted

        clearSubTaskRemindersForTask(record.taskId)
        // A reopened task intentionally keeps its previous record until the next
        // successful completion so Calendar undo can restore it without losing
        // text or a private photo. A new completion replaces that dormant record.
        upsertCompletionRecord(record)
        return CompletionCommitStatus.Completed
    }

    @Transaction
    suspend fun reopenTask(taskId: Int): ReopenTaskDbResult {
        val task = getTaskEntity(taskId) ?: return ReopenTaskDbResult(reopened = false)
        if (!task.isDone) return ReopenTaskDbResult(reopened = false)

        val completion = getCompletionRecord(taskId)
        val changed = markTaskPending(taskId)
        if (changed == 0) return ReopenTaskDbResult(reopened = false)

        // Keep the record as a recoverable draft. It remains hidden while the
        // task is pending and is replaced atomically on the next completion.
        return ReopenTaskDbResult(reopened = true, photoId = completion?.photoId)
    }

    /** Reads the current completion inside the same transaction as deletion. */
    @Transaction
    suspend fun deleteTaskAndReturnPhotoId(taskId: Int): String? {
        val task = getTaskEntity(taskId) ?: return null
        val photoId = getCompletionRecord(taskId)?.photoId
        deleteTaskEntity(task)
        return photoId
    }

    // ── One-shot queries (for backup) ────────────────────────────────

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY position ASC")
    suspend fun getAllTasksOnce(): List<TaskWithSubTasks>
}
