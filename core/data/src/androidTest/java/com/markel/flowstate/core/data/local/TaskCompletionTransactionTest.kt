package com.markel.flowstate.core.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RED contract tests for the one-to-one task completion record transaction.
 *
 * These tests intentionally target the v21 API before its production types and
 * DAO methods exist. They must fail until task state, reminders, and the
 * completion record are committed as one Room transaction.
 */
@RunWith(AndroidJUnit4::class)
class TaskCompletionTransactionTest {

    private lateinit var database: FlowStateDatabase
    private lateinit var dao: TaskDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FlowStateDatabase::class.java,
        ).build()
        dao = database.taskDao
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun commitTaskCompletion_updatesTask_clearsAllReminders_andStoresRecordAtomically() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        val record = TaskCompletionRecordEntity(
            taskId = taskId,
            note = "第一次独自看电影",
            photoId = "11111111-1111-1111-1111-111111111111.jpg",
            completedAt = 1_790_000_000_000L,
        )

        val result = dao.commitTaskCompletion(record)

        assertEquals(CompletionCommitStatus.Completed, result)
        val savedTask = requireNotNull(dao.getTaskById(taskId))
        assertTrue(savedTask.task.isDone)
        assertEquals(record.completedAt, savedTask.task.completedAt)
        assertNull(savedTask.task.reminderTime)
        assertTrue(savedTask.subTasks.all { it.reminderTime == null })
        assertEquals(record, dao.observeCompletion(taskId).first())
    }

    @Test
    fun commitTaskCompletion_whenAlreadyCompleted_preservesTheFirstRecord() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        val first = TaskCompletionRecordEntity(
            taskId = taskId,
            note = "第一次记录",
            photoId = "22222222-2222-2222-2222-222222222222.jpg",
            completedAt = 1_790_000_000_100L,
        )
        val second = TaskCompletionRecordEntity(
            taskId = taskId,
            note = "不应覆盖第一次记录",
            photoId = "33333333-3333-3333-3333-333333333333.jpg",
            completedAt = 1_790_000_000_200L,
        )

        assertEquals(CompletionCommitStatus.Completed, dao.commitTaskCompletion(first))
        val duplicateResult = dao.commitTaskCompletion(second)

        assertEquals(CompletionCommitStatus.AlreadyCompleted, duplicateResult)
        assertEquals(first, dao.observeCompletion(taskId).first())
        assertEquals(first.completedAt, dao.getTaskById(taskId)?.task?.completedAt)
    }

    @Test
    fun commitTaskCompletion_whenTaskDoesNotExist_returnsNotFoundWithoutOrphanRecord() = runBlocking {
        val missingTaskId = 404
        val record = TaskCompletionRecordEntity(
            taskId = missingTaskId,
            note = null,
            photoId = null,
            completedAt = 1_790_000_000_300L,
        )

        val result = dao.commitTaskCompletion(record)

        assertEquals(CompletionCommitStatus.NotFound, result)
        assertNull(dao.observeCompletion(missingTaskId).first())
    }

    @Test
    fun commitTaskCompletion_whenRecordInsertFails_rollsBackTaskAndReminderChanges() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_task_completion_insert
            BEFORE INSERT ON task_completion_records
            BEGIN
                SELECT RAISE(ABORT, 'forced completion-record failure');
            END
            """.trimIndent(),
        )

        try {
            dao.commitTaskCompletion(
                TaskCompletionRecordEntity(
                    taskId = taskId,
                    note = "这次写入应失败",
                    photoId = null,
                    completedAt = 1_790_000_000_400L,
                ),
            )
            fail("Expected the completion-record insert to abort the transaction")
        } catch (_: Exception) {
            // Expected: assertions below prove the preceding updates rolled back.
        }

        val unchanged = requireNotNull(dao.getTaskById(taskId))
        assertFalse(unchanged.task.isDone)
        assertNull(unchanged.task.completedAt)
        assertEquals(123_456L, unchanged.task.reminderTime)
        assertEquals(listOf(234_567L, 345_678L), unchanged.subTasks.map { it.reminderTime })
        assertNull(dao.observeCompletion(taskId).first())
    }

    @Test
    fun reopenTask_retainsRecoverableRecord_returnsPhotoId_andDoesNotRestoreReminders() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        val photoId = "44444444-4444-4444-4444-444444444444.jpg"
        dao.commitTaskCompletion(
            TaskCompletionRecordEntity(
                taskId = taskId,
                note = "稍后重新做一次",
                photoId = photoId,
                completedAt = 1_790_000_000_500L,
            ),
        )

        val result = dao.reopenTask(taskId)

        assertTrue(result.reopened)
        assertEquals(photoId, result.photoId)
        val reopened = requireNotNull(dao.getTaskById(taskId))
        assertFalse(reopened.task.isDone)
        assertNull(reopened.task.completedAt)
        assertNull(reopened.task.reminderTime)
        assertTrue(reopened.subTasks.all { it.reminderTime == null })
        assertEquals(photoId, dao.observeCompletion(taskId).first()?.photoId)
    }

    @Test
    fun completingAgainAfterReopen_replacesTheRecoverableRecord() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        val first = TaskCompletionRecordEntity(
            taskId = taskId,
            note = "first",
            photoId = "55555555-5555-4555-8555-555555555555.jpg",
            completedAt = 1_790_000_000_700L,
        )
        val second = TaskCompletionRecordEntity(
            taskId = taskId,
            note = "second",
            photoId = null,
            completedAt = 1_790_000_000_800L,
        )
        assertEquals(CompletionCommitStatus.Completed, dao.commitTaskCompletion(first))
        assertTrue(dao.reopenTask(taskId).reopened)

        assertEquals(CompletionCommitStatus.Completed, dao.commitTaskCompletion(second))

        assertEquals(second, dao.observeCompletion(taskId).first())
        assertTrue(requireNotNull(dao.getTaskById(taskId)).task.isDone)
    }

    @Test
    fun deletingTask_cascadesItsCompletionRecord() = runBlocking {
        val taskId = insertPendingTaskWithReminders()
        dao.commitTaskCompletion(
            TaskCompletionRecordEntity(
                taskId = taskId,
                note = null,
                photoId = null,
                completedAt = 1_790_000_000_600L,
            ),
        )

        dao.deleteTaskEntity(requireNotNull(dao.getTaskById(taskId)).task)

        assertNull(dao.observeCompletion(taskId).first())
    }

    private suspend fun insertPendingTaskWithReminders(): Int {
        val taskId = dao.upsertTaskEntity(
            TaskEntity(
                title = "30 天挑战",
                isDone = false,
                reminderTime = 123_456L,
            ),
        ).toInt()
        dao.insertSubTasks(
            listOf(
                SubTaskEntity(
                    id = "sub-1",
                    taskId = taskId,
                    title = "准备",
                    description = "",
                    isDone = false,
                    priority = 0,
                    dueDate = null,
                    position = 0,
                    reminderTime = 234_567L,
                ),
                SubTaskEntity(
                    id = "sub-2",
                    taskId = taskId,
                    title = "出发",
                    description = "",
                    isDone = false,
                    priority = 0,
                    dueDate = null,
                    position = 1,
                    reminderTime = 345_678L,
                ),
            ),
        )
        return taskId
    }
}
