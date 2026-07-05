package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of the "third point of reference" the sync engine (`core:sync`'s `SyncPlanner`) needs to
 * tell a deletion apart from a fresh file on the other side. See `SyncStateRepository` in `:core:sync`.
 */
@Entity(
    tableName = "sync_state",
    indices = [Index(value = ["profileId", "relativePath"], unique = true)],
)
data class SyncStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val relativePath: String,
    val localSize: Long?,
    val localLastModified: Long?,
    val remoteSize: Long?,
    val remoteLastModified: Long?,
    val checksum: String?,
    val lastSyncedAt: Long,
)
