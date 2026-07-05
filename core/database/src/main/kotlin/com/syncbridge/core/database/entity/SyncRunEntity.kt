package com.syncbridge.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per executed sync (manual, scheduled, or real-time-triggered) — backs the History screen. */
@Entity(tableName = "sync_runs", indices = [Index("profileId")])
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
    val filesScanned: Int,
    val filesUploaded: Int,
    val filesDownloaded: Int,
    val filesSkipped: Int,
    val filesDeleted: Int,
    val filesConflicted: Int,
    val errorsCount: Int,
    val bytesUploaded: Long,
    val bytesDownloaded: Long,
)
