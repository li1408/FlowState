package com.markel.flowstate.core.domain.usecase.habits

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitDashboardData
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.HabitStreakCalculator
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.HabitWithStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class GetHabitsWithStatusUseCase @Inject constructor(
    private val repository: HabitRepository
) {
    operator fun invoke(date: LocalDate = LocalDate.now()): Flow<List<HabitWithStatus>> {
        return observeDashboard(date).map { it.habits }
    }

    /**
     * Observes the dashboard through exactly one habits query, one boolean
     * entries query and one numeric entries query. Expensive history grouping
     * and streak calculations run away from the main thread.
     */
    fun observeDashboard(date: LocalDate = LocalDate.now()): Flow<HabitDashboardData> {
        return observeDashboardOnDates(flowOf(date))
    }

    /** Recomputes the dashboard whenever the active local date changes. */
    fun observeDashboardOnDates(dates: Flow<LocalDate>): Flow<HabitDashboardData> {
        return combine(
            repository.getHabits(),
            repository.getAllEntries(),
            repository.getAllNumericEntries(),
            dates,
        ) { habits, booleanEntries, numericEntries, date ->
            val booleanEntriesByHabit = booleanEntries.groupBy { it.habitId }
            val numericEntriesByHabit = numericEntries.groupBy { it.habitId }
            HabitDashboardData(
                habits = buildStatus(
                    habits = habits,
                    boolEntriesByHabit = booleanEntriesByHabit,
                    numericByHabit = numericEntriesByHabit,
                    date = date,
                ),
                date = date,
                booleanEntriesByHabit = booleanEntriesByHabit,
                numericEntriesByHabit = numericEntriesByHabit,
            )
        }.flowOn(Dispatchers.Default)
    }

    private fun buildStatus(
        habits: List<Habit>,
        boolEntriesByHabit: Map<Int, List<HabitEntryFlat>>,
        numericByHabit: Map<Int, List<HabitNumericEntry>>,
        date: LocalDate
    ): List<HabitWithStatus> {
        val today = date.toEpochDay()

        return habits
            .sortedBy { it.position }
            .map { habit ->
                when (habit.habitType) {
                    HabitType.BOOLEAN -> {
                        val entries = boolEntriesByHabit[habit.id] ?: emptyList()
                        val isCompletedToday = entries.any { it.epochDay == today }
                        val completedDates = entries.map { LocalDate.ofEpochDay(it.epochDay) }
                        val streak = HabitStreakCalculator.current(
                            completedDates = completedDates,
                            scheduledDays = habit.scheduledDays,
                            today = date
                        )
                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = isCompletedToday,
                            streak = streak
                        )
                    }
                    HabitType.NUMERIC -> {
                        val entries = numericByHabit[habit.id] ?: emptyList()
                        val entriesByDay = entries.associateBy { it.date.toEpochDay() }
                        val todayValue = entriesByDay[today]?.value

                        // Completed if there is a value today and is bigger than the goal (or simply have a value without goal)
                        val isCompletedToday = when {
                            todayValue == null -> false
                            habit.targetValue != null -> todayValue >= habit.targetValue
                            else -> todayValue > 0f
                        }

                        val completedDates = HabitStreakCalculator.qualifyingNumericDates(
                            entries = entries,
                            targetValue = habit.targetValue
                        )
                        val streak = HabitStreakCalculator.current(
                            completedDates = completedDates,
                            scheduledDays = habit.scheduledDays,
                            today = date
                        )

                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = isCompletedToday,
                            streak = streak,
                            todayValue = todayValue
                        )
                    }
                }
            }
    }

}
