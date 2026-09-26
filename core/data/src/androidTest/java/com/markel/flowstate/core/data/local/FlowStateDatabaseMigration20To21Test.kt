package com.markel.flowstate.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FlowStateDatabaseMigration20To21Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlowStateDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate20To21_preservesTasksAndBackfillsEveryCompletedTask() {
        helper.createDatabase(TEST_DATABASE, 20).apply {
            insertTask(id = 1, title = "已完成", isDone = true, completedAt = 1_790_000_000_000L)
            insertTask(id = 2, title = "待完成", isDone = false, completedAt = null)
            insertTask(id = 3, title = "异常旧数据", isDone = true, completedAt = null)
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            21,
            true,
            FlowStateDatabase.MIGRATION_20_21,
        )

        database.query("SELECT title, isDone, completedAt FROM tasks ORDER BY id").use { cursor ->
            assertEquals(3, cursor.count)
            cursor.moveToFirst()
            assertEquals("已完成", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1_790_000_000_000L, cursor.getLong(2))
            cursor.moveToNext()
            assertEquals("待完成", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertTrueNull(cursor, 2)
            cursor.moveToNext()
            assertEquals("异常旧数据", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertTrueNull(cursor, 2)
        }

        database.query(
            "SELECT taskId, note, photoId, completedAt FROM task_completion_records ORDER BY taskId",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getString(2))
            assertEquals(1_790_000_000_000L, cursor.getLong(3))
            cursor.moveToNext()
            assertEquals(3, cursor.getInt(0))
            assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getString(2))
            assertEquals(0L, cursor.getLong(3))
        }

        database.execSQL("DELETE FROM tasks WHERE id = 1")
        database.query("SELECT COUNT(*) FROM task_completion_records WHERE taskId = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.insertTask(
        id: Int,
        title: String,
        isDone: Boolean,
        completedAt: Long?,
    ) {
        execSQL(
            """
            INSERT INTO tasks (
                id, title, description, isDone, position, priority,
                dueDate, completedAt, reminderTime, categoryId
            ) VALUES (?, ?, '', ?, ?, 0, NULL, ?, NULL, 1)
            """.trimIndent(),
            arrayOf(id, title, if (isDone) 1 else 0, id, completedAt),
        )
    }

    private fun assertTrueNull(cursor: android.database.Cursor, column: Int) {
        assertFalse(!cursor.isNull(column))
    }

    companion object {
        private const val TEST_DATABASE = "migration-20-21"
    }
}
