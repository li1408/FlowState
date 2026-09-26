package com.markel.flowstate.core.data.backup

import androidx.room.withTransaction
import com.markel.flowstate.core.data.local.FlowStateDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Runs one complete backup database operation against a single Room snapshot. */
interface BackupTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomBackupTransactionRunner @Inject constructor(
    private val database: FlowStateDatabase,
) : BackupTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction(block)
}

internal object DirectBackupTransactionRunner : BackupTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
