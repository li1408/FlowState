package com.markel.flowstate.benchmarking

import com.markel.flowstate.BuildConfig
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.HabitEntity
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Deterministic data for Macrobenchmark and Baseline Profile journeys.
 *
 * The reset is guarded by the isolated `.benchmark` application id, so the
 * production database can never be cleared by the exported launcher intent.
 */
class BenchmarkFixtureSeeder @Inject constructor(
    private val habitDao: HabitDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend fun resetIfBenchmarkBuild(today: LocalDate = LocalDate.now()) {
        resetForApplicationId(BuildConfig.APPLICATION_ID, today)
    }

    internal suspend fun resetForApplicationId(
        applicationId: String,
        today: LocalDate = LocalDate.now(),
    ) {
        if (applicationId != BENCHMARK_APPLICATION_ID) return
        userPreferencesRepository.resetForBenchmarkDefaults()
        habitDao.replaceHabitsForBenchmark(createBenchmarkHabits(today))
    }

    companion object {
        const val RESET_EXTRA = "com.markel.flowstate.extra.RESET_BENCHMARK_FIXTURES"
        private const val BENCHMARK_APPLICATION_ID = "com.markel.flowstate.benchmark"
    }
}

internal fun createBenchmarkHabits(today: LocalDate): List<HabitEntity> {
    val createdAtMillis = today.minusDays(60).toEpochDay() * MILLIS_PER_DAY
    return (0 until BENCHMARK_HABIT_COUNT).map { index ->
        val isNumeric = index == 1
        HabitEntity(
            id = BENCHMARK_FIRST_HABIT_ID + index,
            name = "Benchmark habit ${index + 1}",
            iconName = if (isNumeric) "water" else "assignment",
            colorArgb = 0xFF12A66A.toInt(),
            createdAt = createdAtMillis,
            habitType = if (isNumeric) "NUMERIC" else "BOOLEAN",
            unit = if (isNumeric) "次" else null,
            targetValue = if (isNumeric) 10f else null,
            step = 1f,
            position = index,
            scheduledDays = DayOfWeek.entries.toSet(),
        )
    }
}

private const val BENCHMARK_HABIT_COUNT = 30
private const val BENCHMARK_FIRST_HABIT_ID = 10_001
private const val MILLIS_PER_DAY = 86_400_000L
