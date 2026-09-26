package com.markel.flowstate.core.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markel.flowstate.core.data.local.FlowStateDatabase
import com.markel.flowstate.core.data.local.TaskCompletionRecordEntity
import com.markel.flowstate.core.data.local.TaskEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTransactionTest {

    private lateinit var database: FlowStateDatabase
    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FlowStateDatabase::class.java,
        ).build()
        repository = BackupRepositoryImpl(
            taskDao = database.taskDao,
            ideaDao = database.ideaDao,
            checkListDao = database.checkListDao,
            habitDao = database.habitDao,
            categoryDao = database.categoryDao,
            transactionRunner = RoomBackupTransactionRunner(database),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun restore_whenLateTaskWriteFails_rollsBackEarlierCategoryInsert() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_backup_task_insert
            BEFORE INSERT ON tasks
            BEGIN
                SELECT RAISE(ABORT, 'forced restore failure');
            END
            """.trimIndent(),
        )
        val backup = emptyExport(
            tasks = listOf(pendingTask(id = 11, categoryId = 7)),
            categories = listOf(CategorySchema(id = 7, name = "Restore-only", position = 0)),
        )

        val result = repository.restoreFromJson(Json.encodeToString(backup))

        assertTrue(result is RestoreResult.Error)
        assertEquals(RestoreErrorType.UNKNOWN, (result as RestoreResult.Error).type)
        assertTrue(database.categoryDao.getAllCategoriesOnce().isEmpty())
        assertTrue(database.taskDao.getAllTasksOnce().isEmpty())
    }

    @Test
    fun restore_sameCompletionEvent_preservesDeviceLocalPhotoId() = runBlocking {
        val completedAt = 1_790_000_000_000L
        val photoId = "22222222-2222-4222-8222-222222222222.jpg"
        database.taskDao.upsertTaskEntity(
            TaskEntity(
                id = 12,
                title = "Completed",
                isDone = true,
                completedAt = completedAt,
            ),
        )
        database.taskDao.upsertCompletionRecord(
            TaskCompletionRecordEntity(
                taskId = 12,
                note = "local",
                photoId = photoId,
                completedAt = completedAt,
            ),
        )
        val task = TaskSchema(
            id = 12,
            title = "Completed",
            description = "",
            isDone = true,
            position = 0,
            priority = 0,
            completedAt = completedAt,
        )
        val backup = emptyExport(
            tasks = listOf(task),
            completionRecords = listOf(
                TaskCompletionRecordSchema(
                    taskId = 12,
                    note = "from backup",
                    photoId = null,
                    completedAt = completedAt,
                ),
            ),
        )

        val result = repository.restoreFromJson(Json.encodeToString(backup))

        assertTrue(result is RestoreResult.Success)
        val restored = requireNotNull(database.taskDao.getCompletionRecord(12))
        assertEquals("from backup", restored.note)
        assertEquals(photoId, restored.photoId)
        assertEquals(completedAt, restored.completedAt)
    }

    private fun pendingTask(id: Int, categoryId: Int?) = TaskSchema(
        id = id,
        title = "Pending",
        description = "",
        isDone = false,
        position = 0,
        priority = 0,
        categoryId = categoryId,
    )

    private fun emptyExport(
        tasks: List<TaskSchema> = emptyList(),
        categories: List<CategorySchema> = emptyList(),
        completionRecords: List<TaskCompletionRecordSchema> = emptyList(),
    ) = FlowStateExport(
        tasks = tasks,
        subTasks = emptyList(),
        ideas = emptyList(),
        checkLists = emptyList(),
        checkListItems = emptyList(),
        habits = emptyList(),
        habitEntries = emptyList(),
        habitNumericEntries = emptyList(),
        categories = categories,
        completionRecords = completionRecords,
    )
}
