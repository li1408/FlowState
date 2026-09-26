package com.markel.flowstate.core.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HabitDaoAtomicUpdateTest {

    private lateinit var database: FlowStateDatabase
    private lateinit var dao: HabitDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FlowStateDatabase::class.java,
        ).build()
        dao = database.habitDao
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun consecutiveIncrementsAndDecrements_accumulateWithoutLostUpdates_andReachZero() = runBlocking {
        val habitId = dao.insertHabit(
            HabitEntity(
                name = "Water",
                habitType = "NUMERIC",
                targetValue = 10f,
            ),
        ).toInt()
        val epochDay = LocalDate.of(2026, 9, 25).toEpochDay()

        repeat(10) {
            dao.adjustNumericEntry(habitId, epochDay, 1f)
        }

        assertEquals(10f, dao.getNumericEntries(habitId).first().single().value)

        repeat(10) {
            dao.adjustNumericEntry(habitId, epochDay, -1f)
        }

        val zeroEntry = dao.getNumericEntries(habitId).first().single()
        assertEquals(0f, zeroEntry.value)
    }

    @Test
    fun updatePositions_updatesTheWholeDirtyRangeInOneTransaction() = runBlocking {
        val firstId = dao.insertHabit(HabitEntity(name = "First", position = 0)).toInt()
        val secondId = dao.insertHabit(HabitEntity(name = "Second", position = 1)).toInt()
        val thirdId = dao.insertHabit(HabitEntity(name = "Third", position = 2)).toInt()

        dao.updatePositions(
            listOf(
                firstId to 2,
                secondId to 0,
                thirdId to 1,
            ),
        )

        assertEquals(
            listOf(secondId, thirdId, firstId),
            dao.getHabits().first().map(HabitEntity::id),
        )
    }

    @Test
    fun insertAndDelete_keepPositionsContinuous() = runBlocking {
        val firstId = dao.insertHabitAtEnd(HabitEntity(name = "First")).toInt()
        val secondId = dao.insertHabitAtEnd(HabitEntity(name = "Second")).toInt()
        val thirdId = dao.insertHabitAtEnd(HabitEntity(name = "Third")).toInt()

        dao.deleteHabitAndCloseGap(requireNotNull(dao.getHabitById(secondId)))

        assertEquals(
            listOf(firstId to 0, thirdId to 1),
            dao.getHabits().first().map { it.id to it.position },
        )
    }

    @Test
    fun deleteAfterReorder_usesCurrentDatabasePositionInsteadOfStaleUiPosition() = runBlocking {
        val firstId = dao.insertHabitAtEnd(HabitEntity(name = "First")).toInt()
        val secondId = dao.insertHabitAtEnd(HabitEntity(name = "Second")).toInt()
        val thirdId = dao.insertHabitAtEnd(HabitEntity(name = "Third")).toInt()
        val staleFirst = requireNotNull(dao.getHabitById(firstId))

        dao.updatePositions(
            listOf(
                secondId to 0,
                thirdId to 1,
                firstId to 2,
            ),
        )
        dao.deleteHabitAndCloseGap(staleFirst)

        assertEquals(
            listOf(secondId to 0, thirdId to 1),
            dao.getHabits().first().map { it.id to it.position },
        )
    }
}
