package com.markel.flowstate.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_completion_records",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskCompletionRecordEntity(
    @PrimaryKey val taskId: Int,
    val note: String? = null,
    val photoId: String? = null,
    val completedAt: Long,
)
