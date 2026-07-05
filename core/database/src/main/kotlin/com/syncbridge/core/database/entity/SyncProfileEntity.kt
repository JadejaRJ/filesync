package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One local-folder-to-remote-folder sync pairing. [filterConfigJson] is a serialized `FileFilterConfig`. */
@Entity(
    tableName = "sync_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["remoteConnectionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("remoteConnectionId")],
)
data class SyncProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val localUri: String,
    val remoteConnectionId: Long,
    val remotePath: String,
    val syncMode: String,
    val conflictRule: String,
    val deleteRule: String,
    val scheduleKind: String,
    val scheduleIntervalMinutes: Long,
    val requireCharging: Boolean,
    val requireWifi: Boolean,
    val minBatteryPercent: Int?,
    val networkRule: String,
    val isRealtimeEnabled: Boolean,
    val isEnabled: Boolean,
    val filterConfigJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncAt: Long?,
    val lastSyncStatus: String?,
    val totalFilesSynced: Long = 0,
    val totalUploadBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalErrors: Long = 0,
)
