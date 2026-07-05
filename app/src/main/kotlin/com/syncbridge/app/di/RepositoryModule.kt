package com.syncbridge.app.di

import com.syncbridge.app.data.repository.KnownHostsRepositoryImpl
import com.syncbridge.app.data.repository.SyncStateRepositoryImpl
import com.syncbridge.core.sync.engine.SyncStateRepository
import com.syncbridge.protocol.sftp.SftpKnownHostsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSyncStateRepository(impl: SyncStateRepositoryImpl): SyncStateRepository

    @Binds
    @Singleton
    abstract fun bindSftpKnownHostsStore(impl: KnownHostsRepositoryImpl): SftpKnownHostsStore
}
