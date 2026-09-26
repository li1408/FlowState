package com.markel.flowstate.core.data

import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.HabitEntity
import com.markel.flowstate.core.data.local.HabitEntryEntity
import com.markel.flowstate.core.data.local.HabitNumericEntryEntity
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitEntryFlat
import com.markel.flowstate.core.domain.HabitNumericEntry
import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.HabitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class HabitRepositoryImpl @Inject constructor(
    private val dao: HabitDao
) : HabitRepository {

    override fun getHabits(): Flow<List<Habit>> =
        dao.getHabits().map { list -> list.map { it.toDomain() } }

    override fun getEntriesForHabit(habitId: Int): Flow<List<LocalDate>> =
        dao.getEntriesForHabit(habitId).map { entries ->
            entries.map { LocalDate.ofEpochDay(it.completedAt) }
        }

    override suspend fun getHabitById(id: Int): Habit? =
        dao.getHabitById(id)?.toDomain()

    override fun getHabitFlow(id: Int): Flow<Habit?> =
        dao.getHabitFlow(id).map { it?.toDomain() }

    override suspend fun insertHabit(habit: Habit) =
        dao.insertHabitAtEnd(habit.toEntity()).let { Unit }

    override suspend fun updateHabit(habit: Habit) =
        dao.updateHabit(habit.toEntity())

    override suspend fun deleteHabit(habit: Habit) =
        dao.deleteHabitAndCloseGap(habit.toEntity())

    override suspend fun toggleEntry(habitId: Int, date: LocalDate) {
        val habit = dao.getHabitById(habitId) ?: return
        if (date.isBefore(habit.creationDate())) return
        if (date.dayOfWeek !in habit.scheduledDays) return
        dao.toggleEntry(habitId, date.toEpochDay())
    }

    override fun getAllEntries(): Flow<List<HabitEntryFlat>> =  // boolean habits only
        dao.getAllEntries().map { list ->
            list.map { HabitEntryFlat(it.habitId, it.epochDay) }
        }

    override fun getAllNumericEntries(): Flow<List<HabitNumericEntry>> =
        dao.getAllNumericEntries().map { list ->
            list.map { it.toDomain() }
        }

    override fun getNumericEntries(habitId: Int): Flow<List<HabitNumericEntry>> =
        dao.getNumericEntries(habitId).map { entries ->
            entries.map { it.toDomain() }
        }

    override suspend fun logNumericEntry(habitId: Int, date: LocalDate, value: Float) {
        val habit = dao.getHabitById(habitId) ?: return
        if (date.isBefore(habit.creationDate())) return
        if (date.dayOfWeek !in habit.scheduledDays) return
        dao.upsertNumericEntry(
            HabitNumericEntryEntity(
                habitId = habitId,
                epochDay = date.toEpochDay(),
                value = value
            )
        )
    }

    override suspend fun adjustNumericEntry(habitId: Int, date: LocalDate, delta: Float) {
        val habit = dao.getHabitById(habitId) ?: return
        if (date.isBefore(habit.creationDate())) return
        if (date.dayOfWeek !in habit.scheduledDays) return
        dao.adjustNumericEntry(habitId, date.toEpochDay(), delta)
    }

    override suspend fun deleteNumericEntry(habitId: Int, date: LocalDate) =
        dao.deleteNumericEntry(habitId, date.toEpochDay())

    override suspend fun updatePositions(positions: List<Pair<Int, Int>>) {
        dao.updatePositions(positions)
    }

    // --- Mappers ---

    private fun HabitEntity.toDomain() = Habit(
        id = id,
        name = name,
        iconName = iconName,
        colorArgb = colorArgb,
        createdAt = LocalDate.ofEpochDay(createdAt / 86400000),
        habitType = HabitType.valueOf(habitType),
        unit = unit,
        targetValue = targetValue,
        step = step,
        position = position,
        scheduledDays = scheduledDays
    )

    private fun HabitEntity.creationDate(): LocalDate =
        LocalDate.ofEpochDay(createdAt / MILLIS_PER_DAY)

    private fun Habit.toEntity() = HabitEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorArgb = colorArgb,
        createdAt = createdAt.toEpochDay() * 86400000,
        habitType = habitType.name,
        unit = unit,
        targetValue = targetValue,
        step = step,
        position = position,
        scheduledDays = scheduledDays
    )

    private fun HabitNumericEntryEntity.toDomain() = HabitNumericEntry(
        habitId = habitId,
        date = LocalDate.ofEpochDay(epochDay),
        value = value
    )

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
