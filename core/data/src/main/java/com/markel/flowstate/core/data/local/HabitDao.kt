package com.markel.flowstate.core.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    /** Replaces only the isolated benchmark package's habits in one transaction. */
    @Transaction
    suspend fun replaceHabitsForBenchmark(habits: List<HabitEntity>) {
        deleteAllHabits()
        habits.forEach { insertHabit(it) }
    }

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habits")
    suspend fun nextHabitPosition(): Int

    @Transaction
    suspend fun insertHabitAtEnd(habit: HabitEntity): Long =
        insertHabit(habit.copy(position = nextHabitPosition()))

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("UPDATE habits SET position = position - 1 WHERE position > :deletedPosition")
    suspend fun closePositionGap(deletedPosition: Int)

    @Query("SELECT position FROM habits WHERE id = :id")
    suspend fun getHabitPosition(id: Int): Int?

    @Transaction
    suspend fun deleteHabitAndCloseGap(habit: HabitEntity) {
        val currentPosition = getHabitPosition(habit.id) ?: return
        deleteHabit(habit)
        closePositionGap(currentPosition)
    }

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("SELECT * FROM habits ORDER BY position ASC, id ASC")
    fun getHabits(): Flow<List<HabitEntity>>

    @Transaction
    @Query("SELECT * FROM habits")
    fun getHabitsWithEntries(): Flow<List<HabitWithEntries>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Int): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitFlow(id: Int): Flow<HabitEntity?>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId")
    fun getEntriesForHabit(habitId: Int): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND completedAt = :epochDay")
    suspend fun getEntry(habitId: Int, epochDay: Long): HabitEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HabitEntryEntity)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND completedAt = :epochDay")
    suspend fun deleteEntry(habitId: Int, epochDay: Long)

    // toggle en una sola transacción
    @Transaction
    suspend fun toggleEntry(habitId: Int, epochDay: Long) {
        val existing = getEntry(habitId, epochDay)
        if (existing != null) {
            deleteEntry(habitId, epochDay)
        } else {
            insertEntry(HabitEntryEntity(habitId = habitId, completedAt = epochDay))
        }
    }

    @Query("SELECT habitId, completedAt as epochDay FROM habit_entries")
    fun getAllEntries(): Flow<List<HabitEntryFlatEntity>>  // only the entries of boolean habits

    @Query("UPDATE habits SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Int, position: Int)

    @Transaction
    suspend fun updatePositions(positions: List<Pair<Int, Int>>) {
        positions.forEach { (id, position) -> updatePosition(id, position) }
    }

    @Query("SELECT * FROM habit_numeric_entries WHERE habitId = :habitId ORDER BY epochDay DESC")
    fun getNumericEntries(habitId: Int): Flow<List<HabitNumericEntryEntity>>

    @Query("SELECT * FROM habit_numeric_entries ORDER BY epochDay DESC")
    fun getAllNumericEntries(): Flow<List<HabitNumericEntryEntity>>  // Numeric entries

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNumericEntry(entry: HabitNumericEntryEntity)

    /**
     * Applies a delta in one SQLite statement so rapid taps cannot overwrite
     * one another. Values are clamped to zero, matching the previous decrement
     * behavior while retaining a zero-valued row.
     */
    @Query(
        """
        INSERT INTO habit_numeric_entries(habitId, epochDay, value)
        VALUES (:habitId, :epochDay, MAX(0, :delta))
        ON CONFLICT(habitId, epochDay) DO UPDATE
        SET value = MAX(0, value + :delta)
        """
    )
    suspend fun adjustNumericEntry(habitId: Int, epochDay: Long, delta: Float)

    @Query("DELETE FROM habit_numeric_entries WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteNumericEntry(habitId: Int, epochDay: Long)

    // ── One-shot queries (for backup) ────────────────────────────────

    @Transaction
    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsOnce(): List<HabitWithEntries>

    @Query("SELECT * FROM habit_entries")
    suspend fun getAllEntriesOnce(): List<HabitEntryEntity>

    @Query("SELECT * FROM habit_numeric_entries")
    suspend fun getAllNumericEntriesOnce(): List<HabitNumericEntryEntity>

}
