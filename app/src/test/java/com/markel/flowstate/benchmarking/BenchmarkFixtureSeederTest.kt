package com.markel.flowstate.benchmarking

import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class BenchmarkFixtureSeederTest {

    @Test
    fun `production application id can never reset habits`() = runTest {
        val dao = mockk<HabitDao>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)

        BenchmarkFixtureSeeder(dao, preferences).resetForApplicationId(
            applicationId = "com.markel.flowstate",
            today = LocalDate.of(2026, 9, 26),
        )

        coVerify(exactly = 0) { dao.replaceHabitsForBenchmark(any()) }
        coVerify(exactly = 0) { preferences.resetForBenchmarkDefaults() }
    }

    @Test
    fun `benchmark reset restores deterministic navigation and thirty habits`() = runTest {
        val dao = mockk<HabitDao>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true)

        BenchmarkFixtureSeeder(dao, preferences).resetForApplicationId(
            applicationId = "com.markel.flowstate.benchmark",
            today = LocalDate.of(2026, 9, 26),
        )

        coVerify(exactly = 1) { preferences.resetForBenchmarkDefaults() }
        coVerify(exactly = 1) {
            dao.replaceHabitsForBenchmark(match { habits ->
                habits.size == 30 &&
                    habits.first().id == 10_001 &&
                    habits.last().id == 10_030
            })
        }
    }

    @Test
    fun `fixture contains thirty ordered habits and both interaction types`() {
        val today = LocalDate.of(2026, 9, 26)

        val fixtures = createBenchmarkHabits(today)

        assertEquals(30, fixtures.size)
        assertEquals((10_001..10_030).toList(), fixtures.map { it.id })
        assertEquals((0 until 30).toList(), fixtures.map { it.position })
        assertEquals("BOOLEAN", fixtures.first().habitType)
        assertEquals("NUMERIC", fixtures[1].habitType)
        assertEquals(10f, fixtures[1].targetValue)
        assertTrue(fixtures.all { it.scheduledDays == DayOfWeek.entries.toSet() })
        assertTrue(fixtures.all { it.createdAt < today.toEpochDay() * 86_400_000L })
    }
}
