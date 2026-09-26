package com.markel.flowstate.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitDashboardData
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.core.domain.usecase.habits.DecrementNumericValueUseCase
import com.markel.flowstate.core.domain.usecase.habits.DeleteHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.DeleteNumericEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.GetHabitsWithStatusUseCase
import com.markel.flowstate.core.domain.usecase.habits.IncrementNumericValueUseCase
import com.markel.flowstate.core.domain.usecase.habits.InsertHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.LogNumericEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.ToggleHabitEntryUseCase
import com.markel.flowstate.core.domain.usecase.habits.UpdateHabitUseCase
import com.markel.flowstate.core.domain.usecase.habits.UpdateHabitsOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val getHabitsWithStatus: GetHabitsWithStatusUseCase,
    private val insertHabit: InsertHabitUseCase,
    private val updateHabit: UpdateHabitUseCase,
    private val deleteHabit: DeleteHabitUseCase,
    private val toggleEntry: ToggleHabitEntryUseCase,
    private val logNumericEntry: LogNumericEntryUseCase,
    private val incrementNumericValue: IncrementNumericValueUseCase,
    private val decrementNumericValue: DecrementNumericValueUseCase,
    private val deleteNumericEntry: DeleteNumericEntryUseCase,
    private val updateHabitsOrder: UpdateHabitsOrderUseCase
) : ViewModel() {

    private val _showAddDialog = MutableStateFlow(false)
    private val _optimisticOrder = MutableStateFlow<List<Int>?>(null)
    private var reorderGeneration = 0L
    private var lastReleasedGeneration = -1L
    private var reorderDirtyRange: IntRange? = null
    private var reorderCommitJob: Job? = null

    private val dashboardState: StateFlow<HabitDashboardSnapshot?> = run {
        var previousSnapshot: HabitDashboardSnapshot? = null
        getHabitsWithStatus.observeDashboardOnDates(observeLocalDates())
            .map { dashboard: HabitDashboardData ->
                buildHabitDashboardSnapshot(
                    habits = dashboard.habits,
                    booleanEntriesByHabit = dashboard.booleanEntriesByHabit,
                    numericEntriesByHabit = dashboard.numericEntriesByHabit,
                    today = dashboard.date,
                    previousSnapshot = previousSnapshot,
                ).also { previousSnapshot = it }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
    }

    val uiState: StateFlow<HabitUiState> = combine(
        dashboardState,
        _showAddDialog,
        _optimisticOrder,
    ) { dashboard, showDialog, optimisticOrder ->
        dashboard?.toUiState(showDialog, optimisticOrder) ?: HabitUiState.Loading
    }.stateIn(
        scope = viewModelScope,
        // dashboardState owns the only five-second grace period. Stacking a
        // second timeout here would keep Room and history calculations alive
        // for roughly ten seconds after the screen leaves composition.
        started = SharingStarted.WhileSubscribed(),
        initialValue = HabitUiState.Loading,
    )

    // ==================================
    // OPERATIONS FOR BOOLEAN HABITS
    // ==================================

    /**
     * Marks the habit completed / incomplete for a specific date
     */
    fun toggleBooleanHabitOnDate(habitId: Int, date: LocalDate) {
        if (isBeforeHabitCreation(habitId, date)) return
        viewModelScope.launch { toggleEntry(habitId, date) }
    }

    // ==================================
    // OPERATIONS FOR NUMERIC HABITS
    // ==================================

    /**
     * Increment numeric habit value on specific date
     */
    fun incrementNumericHabit(habitId: Int, date: LocalDate, step: Float) {
        if (isBeforeHabitCreation(habitId, date)) return
        viewModelScope.launch {
            incrementNumericValue(habitId, date, step)
        }
    }

    /**
     * Decrement numeric habit value on specific date
     */
    fun decrementNumericHabit(habitId: Int, date: LocalDate, step: Float) {
        if (isBeforeHabitCreation(habitId, date)) return
        viewModelScope.launch {
            decrementNumericValue(habitId, date, step)
        }
    }

    /**
     * Set habit value for specific date
     */
    fun setNumericValue(habitId: Int, date: LocalDate, value: Float) {
        if (isBeforeHabitCreation(habitId, date)) return
        viewModelScope.launch {
            logNumericEntry(habitId, date, value)
        }
    }

    /**
     * Deletes a numeric entry for a specific date
     */
    fun deleteNumericEntry(habitId: Int, date: LocalDate) {
        viewModelScope.launch {
            deleteNumericEntry.invoke(habitId, date)
        }
    }

    // ===================================
    // COMMON OPERATIONS (HABIT CRUD)
    // ===================================

    /**
     * Creates a new habit
     */
    fun addHabit(
        name: String, iconName: String,
        colorArgb: Int,
        habitType: HabitType = HabitType.BOOLEAN,
        unit: String? = null, targetValue: Float? = null,
        step: Float = 1f,
        scheduledDays: Set<DayOfWeek> = DayOfWeek.entries.toSet())
    {
        if (name.isBlank() || scheduledDays.isEmpty()) return
        viewModelScope.launch {
            insertHabit(
                Habit(
                    name = name,
                    iconName = iconName,
                    colorArgb = colorArgb,
                    habitType = habitType,
                    unit = unit,
                    targetValue = targetValue,
                    step = step,
                    scheduledDays = scheduledDays
                )
            )
            _showAddDialog.value = false
        }
    }

    /**
     * Edits an existing habit
     */
    fun editHabit(
        habit: Habit,
        newName: String,
        newIcon: String,
        newColorArgb: Int,
        newUnit: String? = null,
        newTargetValue: Float? = null,
        newStep: Float? = null,
        newScheduledDays: Set<DayOfWeek> = habit.scheduledDays
    ) {
        if (newName.isBlank() || newScheduledDays.isEmpty()) return
        viewModelScope.launch {
            updateHabit(
                habit.copy(
                    name = newName,
                    iconName = newIcon,
                    colorArgb = newColorArgb,
                    unit = newUnit,
                    targetValue = newTargetValue,
                    step = newStep ?: habit.step,
                    scheduledDays = newScheduledDays
                )
            )
        }
    }

    /**
     * Deletes a habit (works for both boolean and numeric types)
     */
    fun deleteHabit(habit: Habit) {
        viewModelScope.launch { deleteHabit.invoke(habit) }
    }

    // ============================================
    // DIALOG CONTROL
    // ============================================

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentState = uiState.value as? HabitUiState.Success ?: return
        val reorderedIds = (_optimisticOrder.value ?: currentState.habits.map { it.habit.id })
            .toMutableList()
        if (fromIndex !in reorderedIds.indices || toIndex !in reorderedIds.indices) return
        if (fromIndex == toIndex) return

        reorderedIds.add(toIndex, reorderedIds.removeAt(fromIndex))
        val changedRange = minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)
        reorderDirtyRange = reorderDirtyRange?.let { dirtyRange ->
            minOf(dirtyRange.first, changedRange.first)..maxOf(dirtyRange.last, changedRange.last)
        } ?: changedRange
        reorderGeneration++
        _optimisticOrder.value = reorderedIds
    }

    fun onReorderStopped() {
        val orderToPersist = _optimisticOrder.value ?: return
        val dirtyRange = reorderDirtyRange ?: return
        val generationToPersist = reorderGeneration
        if (lastReleasedGeneration == generationToPersist) return
        lastReleasedGeneration = generationToPersist
        val previousCommit = reorderCommitJob
        reorderCommitJob = viewModelScope.launch {
            // A newer release is always written after an older one, even if the
            // older Room transaction had already started when it was cancelled.
            previousCommit?.cancelAndJoin()
            val persistedPositionById = dashboardState.value
                ?.habits
                ?.associate { it.habit.id to it.habit.position }
                .orEmpty()
            updateHabitsOrder(
                orderToPersist.mapIndexedNotNull { position, id ->
                    if (
                        position in dirtyRange ||
                        persistedPositionById[id] != position
                    ) {
                        id to position
                    } else {
                        null
                    }
                }
            )

            // Keep the optimistic order until Room has emitted the committed
            // transaction. This avoids a one-frame snap back to stale positions.
            dashboardState
                .filterNotNull()
                .map { dashboard -> dashboard.habits.map { it.habit.id } }
                .first { persistedOrder -> persistedOrder.matchesOrder(orderToPersist) }

            if (
                generationToPersist == reorderGeneration &&
                _optimisticOrder.value == orderToPersist
            ) {
                _optimisticOrder.value = null
                reorderDirtyRange = null
            }
        }
    }

    private fun isBeforeHabitCreation(habitId: Int, date: LocalDate): Boolean {
        val createdAt = dashboardState.value
            ?.habits
            ?.firstOrNull { it.habit.id == habitId }
            ?.habit
            ?.createdAt
            ?: return false
        return date.isBefore(createdAt)
    }
}

private fun HabitDashboardSnapshot.toUiState(
    showDialog: Boolean,
    optimisticOrder: List<Int>?,
): HabitUiState.Success {
    val displayedCards = if (optimisticOrder == null) {
        habitCards
    } else {
        val cardsById = habitCards.associateBy { it.habitWithStatus.habit.id }
        val ordered = buildList {
            optimisticOrder.mapNotNullTo(this) { cardsById[it] }
            habitCards.filterTo(this) { it.habitWithStatus.habit.id !in optimisticOrder }
        }
        ordered.mapIndexed { index, card ->
            val habitWithStatus = card.habitWithStatus
            if (habitWithStatus.habit.position == index) {
                card
            } else {
                card.copy(
                    habitWithStatus = habitWithStatus.copy(
                        habit = habitWithStatus.habit.copy(position = index),
                    ),
                )
            }
        }
    }

    return HabitUiState.Success(
        habits = if (optimisticOrder == null) {
            habits
        } else {
            displayedCards.map { it.habitWithStatus }
        },
        weekEntriesByHabit = weekEntriesByHabit,
        numericEntriesByHabit = numericEntriesByHabit,
        habitCards = displayedCards,
        showAddDialog = showDialog,
        completedToday = completedToday,
        totalHabits = totalHabits,
        motivationalMessageIndex = motivationalMessageIndex,
    )
}

private fun List<Int>.matchesOrder(expectedOrder: List<Int>): Boolean {
    val actualIds = toSet()
    val expectedIds = expectedOrder.toSet()
    return filter { it in expectedIds } == expectedOrder.filter { it in actualIds }
}

/**
 * Emits immediately and again at the next local-day boundary. The one-minute
 * upper bound also reacts to a runtime time-zone or wall-clock change instead
 * of sleeping against a stale zone until the following midnight.
 */
internal fun observeLocalDates(): kotlinx.coroutines.flow.Flow<LocalDate> = flow {
    var lastDate: LocalDate? = null
    while (currentCoroutineContext().isActive) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val currentDate = now.toLocalDate()
        if (currentDate != lastDate) {
            emit(currentDate)
            lastDate = currentDate
        }
        val nextMidnight = currentDate.plusDays(1).atStartOfDay(zone)
        val untilMidnightMillis = Duration.between(now, nextMidnight)
            .toMillis()
            .coerceAtLeast(1_000L)
        delay(minOf(untilMidnightMillis, 60_000L))
    }
}
