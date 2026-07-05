package com.syncbridge.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.syncbridge.core.database.dao.ConnectionDao
import com.syncbridge.core.database.dao.KnownHostDao
import com.syncbridge.core.database.dao.RecycleBinDao
import com.syncbridge.core.database.dao.SyncErrorDao
import com.syncbridge.core.database.dao.SyncProfileDao
import com.syncbridge.core.database.dao.SyncRunDao
import com.syncbridge.core.database.dao.SyncStateDao
import com.syncbridge.core.database.dao.TransferQueueDao
import com.syncbridge.core.database.entity.ConnectionEntity
import com.syncbridge.core.database.entity.KnownHostEntity
import com.syncbridge.core.database.entity.RecycleBinEntity
import com.syncbridge.core.database.entity.SyncErrorEntity
import com.syncbridge.core.database.entity.SyncProfileEntity
import com.syncbridge.core.database.entity.SyncRunEntity
import com.syncbridge.core.database.entity.SyncStateEntity
import com.syncbridge.core.database.entity.TransferQueueEntity

@Database(
    entities = [
        ConnectionEntity::class,
        SyncProfileEntity::class,
        SyncStateEntity::class,
        SyncRunEntity::class,
        SyncErrorEntity::class,
        TransferQueueEntity::class,
        KnownHostEntity::class,
        RecycleBinEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SyncBridgeDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun syncProfileDao(): SyncProfileDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun syncRunDao(): SyncRunDao
    abstract fun syncErrorDao(): SyncErrorDao
    abstract fun transferQueueDao(): TransferQueueDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun recycleBinDao(): RecycleBinDao

    companion object {
        const val DATABASE_NAME = "syncbridge.db"

        /**
         * No migrations exist yet because this is schema version 1 (see spec section 30's MVP scope).
         * Before shipping a version-2 schema change, replace this with real `Migration` objects —
         * destructively wiping sync history/state on upgrade is not acceptable once users have data.
         */
        fun build(context: Context): SyncBridgeDatabase =
            Room.databaseBuilder(context.applicationContext, SyncBridgeDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
