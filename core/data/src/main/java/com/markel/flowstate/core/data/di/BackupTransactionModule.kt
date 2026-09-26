package com.markel.flowstate.core.data.di

import com.markel.flowstate.core.data.backup.BackupTransactionRunner
import com.markel.flowstate.core.data.backup.RoomBackupTransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupTransactionModule {
    @Binds
    abstract fun bindBackupTransactionRunner(
        implementation: RoomBackupTransactionRunner,
    ): BackupTransactionRunner
}
