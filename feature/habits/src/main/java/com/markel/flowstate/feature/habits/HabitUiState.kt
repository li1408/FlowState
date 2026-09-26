package com.markel.flowstate.feature.habits

import androidx.compose.runtime.Immutable
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitWithStatus
import com.markel.flowstate.core.domain.isScheduledFor
import java.time.LocalDate

@Immutable
data class HabitCardUiModel(
    val habitWithStatus: HabitWithStatus,
    val weekEntries: Set<Long>,
    val numericEntries: List<HabitNumericEntry>,
)

@Immutable
data class HabitDashboardSnapshot(
    val habits: List<HabitWithStatus>,
    val weekEntriesByHabit: Map<Int, Set<Long>>,
    val numericEntriesByHabit: Map<Int, List<HabitNumericEntry>>,
    val habitCards: List<HabitCardUiModel>,
    val completedToday: Int,
    val totalHabits: Int,
    val motivationalMessageIndex: Int,
)

internal fun buildHabitDashboardSnapshot(
    habits: List<HabitWithStatus>,
    booleanEntriesByHabit: Map<Int, List<HabitEntryFlat>>,
    numericEntriesByHabit: Map<Int, List<HabitNumericEntry>>,
    today: LocalDate,
    previousSnapshot: HabitDashboardSnapshot? = null,
): HabitDashboardSnapshot {
    val habitsDueToday = habits.filter { it.habit.isScheduledFor(today) }
    val weekEntriesByHabit = booleanEntriesByHabit.mapValues { (_, entries) ->
        entries.asSequence().map(HabitEntryFlat::epochDay).toSet()
    }
    val previousCardsById = previousSnapshot?.habitCards
        ?.associateBy { it.habitWithStatus.habit.id }
        .orEmpty()
    val habitCards = habits.map { habitWithStatus ->
        val habitId = habitWithStatus.habit.id
        val weekEntries = weekEntriesByHabit[habitId].orEmpty()
        val numericEntries = numericEntriesByHabit[habitId].orEmpty()
        val previousCard = previousCardsById[habitId]
        if (
            previousCard != null &&
            previousCard.habitWithStatus == habitWithStatus &&
            previousCard.weekEntries == weekEntries &&
            previousCard.numericEntries == numericEntries
        ) {
            previousCard
        } else {
            HabitCardUiModel(
                habitWithStatus = habitWithStatus,
                weekEntries = weekEntries,
                numericEntries = numericEntries,
            )
        }
    }
    return HabitDashboardSnapshot(
        habits = habits,
        weekEntriesByHabit = weekEntriesByHabit,
        numericEntriesByHabit = numericEntriesByHabit,
        habitCards = habitCards,
        completedToday = habitsDueToday.count { it.isCompletedToday },
        totalHabits = habitsDueToday.size,
        motivationalMessageIndex = today.dayOfYear % 7,
    )
}

sealed interface HabitUiState {
    data object Loading : HabitUiState
    @Immutable
    data class Success(
        val habits: List<HabitWithStatus>,
        val weekEntriesByHabit: Map<Int, Set<Long>>,
        val numericEntriesByHabit: Map<Int, List<HabitNumericEntry>>,
        val habitCards: List<HabitCardUiModel>,
        val showAddDialog: Boolean = false,
        val completedToday: Int = 0,
        val totalHabits: Int = 0,
        val motivationalMessageIndex: Int = 0
    ) : HabitUiState
}
