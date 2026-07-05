package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Files still pending or retrying transfer, surviving process death — the foreground service and
 * WorkManager worker both resume from this table rather than losing queued work if Android kills them.
 */
@Entity(tableName = "transfer_queue", indices = [Index("profileId"), Index("status")])
data class TransferQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val relativePath: String,
    val operation: String,
    val status: String,
    val retryCount: Int,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
