package com.markel.flowstate.feature.habits

import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.HabitWithStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitDashboardSnapshotTest {

    @Test
    fun buildHabitDashboardSnapshot_mergesBooleanNumericAndStatusData() {
        val today = LocalDate.of(2026, 9, 21) // Monday
        val booleanHabit = habit(
            id = 1,
            name = "Read",
            type = HabitType.BOOLEAN,
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )
        val numericHabit = habit(
            id = 2,
            name = "Water",
            type = HabitType.NUMERIC,
            scheduledDays = setOf(DayOfWeek.MONDAY)
        )
        val notDueToday = habit(
            id = 3,
            name = "Run",
            type = HabitType.BOOLEAN,
            scheduledDays = setOf(DayOfWeek.TUESDAY)
        )
        val habits = listOf(
            HabitWithStatus(booleanHabit, isCompletedToday = true),
            HabitWithStatus(numericHabit, isCompletedToday = true, todayValue = 2f),
            HabitWithStatus(notDueToday, isCompletedToday = true)
        )
        val booleanEntries = listOf(
            HabitEntryFlat(booleanHabit.id, today.toEpochDay()),
            HabitEntryFlat(booleanHabit.id, today.minusDays(7).toEpochDay()),
            HabitEntryFlat(notDueToday.id, today.minusDays(6).toEpochDay())
        )
        val numericEntries = listOf(
            HabitNumericEntry(numericHabit.id, today, 2f),
            HabitNumericEntry(numericHabit.id, today.minusDays(7), 1f)
        )

        val snapshot = buildHabitDashboardSnapshot(
            habits = habits,
            booleanEntriesByHabit = booleanEntries.groupBy { it.habitId },
            numericEntriesByHabit = numericEntries.groupBy { it.habitId },
            today = today
        )

        assertEquals(habits, snapshot.habits)
        assertEquals(
            setOf(today.toEpochDay(), today.minusDays(7).toEpochDay()),
            snapshot.weekEntriesByHabit[booleanHabit.id]
        )
        assertEquals(numericEntries, snapshot.numericEntriesByHabit[numericHabit.id])
        assertEquals(2, snapshot.completedToday)
        assertEquals(2, snapshot.totalHabits)
        assertEquals(today.dayOfYear % 7, snapshot.motivationalMessageIndex)
        assertFalse(
            "Dialog state must remain outside the expensive dashboard snapshot",
            snapshot.javaClass.declaredFields.any { it.name == "showAddDialog" }
        )
    }

    @Test
    fun buildHabitDashboardSnapshot_reusesCardsWhoseDataDidNotChange() {
        val today = LocalDate.of(2026, 9, 21)
        val firstHabit = habit(1, "Read", HabitType.BOOLEAN, setOf(DayOfWeek.MONDAY))
        val secondHabit = habit(2, "Run", HabitType.BOOLEAN, setOf(DayOfWeek.MONDAY))
        val statuses = listOf(
            HabitWithStatus(firstHabit, isCompletedToday = false),
            HabitWithStatus(secondHabit, isCompletedToday = false),
        )
        val before = buildHabitDashboardSnapshot(statuses, emptyMap(), emptyMap(), today)

        val after = buildHabitDashboardSnapshot(
            habits = statuses,
            booleanEntriesByHabit = mapOf(
                firstHabit.id to listOf(HabitEntryFlat(firstHabit.id, today.toEpochDay())),
            ),
            numericEntriesByHabit = emptyMap(),
            today = today,
            previousSnapshot = before,
        )

        assertNotSame(before.habitCards[0], after.habitCards[0])
        assertSame(before.habitCards[1], after.habitCards[1])
    }

    private fun habit(
        id: Int,
        name: String,
        type: HabitType,
        scheduledDays: Set<DayOfWeek>
    ) = Habit(
        id = id,
        name = name,
        habitType = type,
        createdAt = LocalDate.of(2026, 9, 1),
        scheduledDays = scheduledDays
    )
}
