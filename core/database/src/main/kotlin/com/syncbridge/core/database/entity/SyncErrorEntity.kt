package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One failed file operation from a sync run — backs the Error Details screen. Mirrors `AppError`'s fields. */
@Entity(tableName = "sync_errors", indices = [Index("runId"), Index("profileId")])
data class SyncErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val profileId: Long,
    val filePath: String,
    val operation: String,
    val errorCode: String,
    val errorMessage: String,
    val suggestedFix: String,
    val retryable: Boolean,
    val createdAt: Long,
)
