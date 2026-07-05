package com.syncbridge.app.di

import android.content.Context
import com.syncbridge.core.database.SyncBridgeDatabase
import com.syncbridge.core.database.dao.ConnectionDao
import com.syncbridge.core.database.dao.KnownHostDao
import com.syncbridge.core.database.dao.RecycleBinDao
import com.syncbridge.core.database.dao.SyncErrorDao
import com.syncbridge.core.database.dao.SyncProfileDao
import com.syncbridge.core.database.dao.SyncRunDao
import com.syncbridge.core.database.dao.SyncStateDao
import com.syncbridge.core.database.dao.TransferQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SyncBridgeDatabase = SyncBridgeDatabase.build(context)

    @Provides
    fun provideConnectionDao(db: SyncBridgeDatabase): ConnectionDao = db.connectionDao()

    @Provides
    fun provideSyncProfileDao(db: SyncBridgeDatabase): SyncProfileDao = db.syncProfileDao()

    @Provides
    fun provideSyncStateDao(db: SyncBridgeDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideSyncRunDao(db: SyncBridgeDatabase): SyncRunDao = db.syncRunDao()

    @Provides
    fun provideSyncErrorDao(db: SyncBridgeDatabase): SyncErrorDao = db.syncErrorDao()

    @Provides
    fun provideTransferQueueDao(db: SyncBridgeDatabase): TransferQueueDao = db.transferQueueDao()

    @Provides
    fun provideKnownHostDao(db: SyncBridgeDatabase): KnownHostDao = db.knownHostDao()

    @Provides
    fun provideRecycleBinDao(db: SyncBridgeDatabase): RecycleBinDao = db.recycleBinDao()
}
