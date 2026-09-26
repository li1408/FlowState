package com.markel.flowstate.core.domain

import com.markel.flowstate.core.domain.usecase.habits.GetHabitsWithStatusUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GetHabitsWithStatusUseCaseTest {

    private val repository: HabitRepository = mockk()
    private val useCase = GetHabitsWithStatusUseCase(repository)
    private val monday = LocalDate.of(2026, 8, 17)
    private val friday = monday.plusDays(4)
    private val schedule = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )

    @Test
    fun booleanHabit_usesScheduledOccurrencesForCurrentStreak() = runTest {
        val habit = habit(type = HabitType.BOOLEAN)
        every { repository.getHabits() } returns flowOf(listOf(habit))
        every { repository.getAllEntries() } returns flowOf(
            listOf(
                HabitEntryFlat(habit.id, monday.toEpochDay()),
                HabitEntryFlat(habit.id, monday.plusDays(2).toEpochDay()),
                HabitEntryFlat(habit.id, friday.toEpochDay())
            )
        )
        every { repository.getAllNumericEntries() } returns flowOf(emptyList())

        val result = useCase(friday).first().single()

        assertTrue(result.isCompletedToday)
        assertEquals(3, result.streak)
    }

    @Test
    fun numericHabit_usesSameScheduledStreakLogic() = runTest {
        val habit = habit(type = HabitType.NUMERIC, targetValue = 10f)
        every { repository.getHabits() } returns flowOf(listOf(habit))
        every { repository.getAllEntries() } returns flowOf(emptyList())
        every { repository.getAllNumericEntries() } returns flowOf(
            listOf(
                numericEntry(monday, 12f),
                numericEntry(monday.plusDays(2), 10f),
                numericEntry(friday, 15f)
            )
        )

        val result = useCase(friday).first().single()

        assertTrue(result.isCompletedToday)
        assertEquals(3, result.streak)
    }

    @Test
    fun numericHabit_valueBelowTargetBreaksScheduledStreak() = runTest {
        val habit = habit(type = HabitType.NUMERIC, targetValue = 10f)
        every { repository.getHabits() } returns flowOf(listOf(habit))
        every { repository.getAllEntries() } returns flowOf(emptyList())
        every { repository.getAllNumericEntries() } returns flowOf(
            listOf(
                numericEntry(monday, 12f),
                numericEntry(monday.plusDays(2), 5f),
                numericEntry(friday, 15f)
            )
        )

        val result = useCase(friday).first().single()

        assertEquals(1, result.streak)
    }

    @Test
    fun dashboard_recomputesStatusWhenLocalDateChanges() = runTest {
        val dailyHabit = habit(type = HabitType.BOOLEAN).copy(
            scheduledDays = DayOfWeek.entries.toSet(),
        )
        every { repository.getHabits() } returns flowOf(listOf(dailyHabit))
        every { repository.getAllEntries() } returns flowOf(
            listOf(HabitEntryFlat(dailyHabit.id, monday.toEpochDay()))
        )
        every { repository.getAllNumericEntries() } returns flowOf(emptyList())
        val dates = MutableStateFlow(monday)
        val firstEmission = CompletableDeferred<Unit>()

        val result = async {
            useCase.observeDashboardOnDates(dates)
                .onEach { firstEmission.complete(Unit) }
                .take(2)
                .toList()
        }
        firstEmission.await()
        dates.value = monday.plusDays(1)

        val emissions = result.await()
        assertTrue(emissions[0].habits.single().isCompletedToday)
        assertFalse(emissions[1].habits.single().isCompletedToday)
        assertEquals(monday.plusDays(1), emissions[1].date)
    }

    private fun habit(type: HabitType, targetValue: Float? = null) = Habit(
        id = 1,
        name = "Exercise",
        habitType = type,
        targetValue = targetValue,
        createdAt = monday.minusWeeks(1),
        scheduledDays = schedule
    )

    private fun numericEntry(date: LocalDate, value: Float) = HabitNumericEntry(
        habitId = 1,
        date = date,
        value = value
    )
}
