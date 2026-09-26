package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.HabitEntity
import com.markel.flowstate.core.data.local.HabitNumericEntryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitRepositoryImplTest {

    private val dao: HabitDao = mockk(relaxed = true)
    private val repository = HabitRepositoryImpl(dao)
    private val monday = LocalDate.of(2026, 8, 17)

    @Test
    fun toggleEntry_onScheduledDay_togglesDaoEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )

        repository.toggleEntry(1, monday)

        coVerify { dao.toggleEntry(1, monday.toEpochDay()) }
    }

    @Test
    fun toggleEntry_onUnscheduledDay_doesNotToggleDaoEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.WEDNESDAY)
        )

        repository.toggleEntry(1, monday)

        coVerify(exactly = 0) { dao.toggleEntry(any(), any()) }
    }

    @Test
    fun toggleEntry_beforeHabitCreation_doesNotToggleDaoEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY),
            createdAt = monday.plusDays(1),
        )

        repository.toggleEntry(1, monday)

        coVerify(exactly = 0) { dao.toggleEntry(any(), any()) }
    }

    @Test
    fun logNumericEntry_onScheduledDay_upsertsEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )

        repository.logNumericEntry(1, monday, 5f)

        coVerify {
            dao.upsertNumericEntry(
                HabitNumericEntryEntity(
                    habitId = 1,
                    epochDay = monday.toEpochDay(),
                    value = 5f
                )
            )
        }
    }

    @Test
    fun logNumericEntry_onUnscheduledDay_doesNotUpsertEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.WEDNESDAY)
        )

        repository.logNumericEntry(1, monday, 5f)

        coVerify(exactly = 0) { dao.upsertNumericEntry(any()) }
    }

    @Test
    fun logNumericEntry_beforeHabitCreation_doesNotUpsertEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY),
            createdAt = monday.plusDays(1),
        )

        repository.logNumericEntry(1, monday, 5f)

        coVerify(exactly = 0) { dao.upsertNumericEntry(any()) }
    }

    @Test
    fun adjustNumericEntry_tenConsecutiveIncrements_accumulateWithoutLosingUpdates() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )
        var storedValue = 0f
        coEvery {
            dao.adjustNumericEntry(1, monday.toEpochDay(), any())
        } answers {
            storedValue = (storedValue + arg<Float>(2)).coerceAtLeast(0f)
        }

        coroutineScope {
            repeat(10) {
                launch {
                    repository.adjustNumericEntry(
                        habitId = 1,
                        date = monday,
                        delta = 1f
                    )
                }
            }
        }

        assertEquals(10f, storedValue, 0f)
        coVerify(exactly = 10) {
            dao.adjustNumericEntry(1, monday.toEpochDay(), 1f)
        }
    }

    @Test
    fun adjustNumericEntry_belowZero_clampsToZeroWithoutDeletingEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )
        var storedValue = 2f
        coEvery {
            dao.adjustNumericEntry(1, monday.toEpochDay(), any())
        } answers {
            storedValue = (storedValue + arg<Float>(2)).coerceAtLeast(0f)
        }

        repository.adjustNumericEntry(
            habitId = 1,
            date = monday,
            delta = -5f
        )

        assertEquals(0f, storedValue, 0f)
        coVerify(exactly = 0) {
            dao.deleteNumericEntry(1, monday.toEpochDay())
        }
    }

    @Test
    fun adjustNumericEntry_beforeHabitCreation_doesNotAdjustEntry() = runTest {
        coEvery { dao.getHabitById(1) } returns habitEntity(
            scheduledDays = setOf(DayOfWeek.MONDAY),
            createdAt = monday.plusDays(1),
        )

        repository.adjustNumericEntry(1, monday, 1f)

        coVerify(exactly = 0) { dao.adjustNumericEntry(any(), any(), any()) }
    }

    private fun habitEntity(
        scheduledDays: Set<DayOfWeek>,
        createdAt: LocalDate = monday.minusDays(7),
    ) = HabitEntity(
        id = 1,
        name = "Exercise",
        createdAt = createdAt.toEpochDay() * 86_400_000L,
        scheduledDays = scheduledDays
    )
}
