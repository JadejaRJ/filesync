package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syncbridge.core.database.entity.SyncProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProfileDao {
    @Query("SELECT * FROM sync_profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<SyncProfileEntity>>

    @Query("SELECT * FROM sync_profiles WHERE isEnabled = 1")
    suspend fun getEnabled(): List<SyncProfileEntity>

    @Query("SELECT * FROM sync_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<SyncProfileEntity?>

    @Query("SELECT * FROM sync_profiles WHERE id = :id")
    suspend fun getById(id: Long): SyncProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SyncProfileEntity): Long

    @Update
    suspend fun update(entity: SyncProfileEntity)

    @Delete
    suspend fun delete(entity: SyncProfileEntity)

    @Query("UPDATE sync_profiles SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query(
        """
        UPDATE sync_profiles SET
            lastSyncAt = :syncedAt,
            lastSyncStatus = :status,
            totalFilesSynced = totalFilesSynced + :filesSynced,
            totalUploadBytes = totalUploadBytes + :uploadBytes,
            totalDownloadBytes = totalDownloadBytes + :downloadBytes,
            totalErrors = totalErrors + :errors
        WHERE id = :id
        """,
    )
    suspend fun recordSyncResult(
        id: Long,
        syncedAt: Long,
        status: String,
        filesSynced: Long,
        uploadBytes: Long,
        downloadBytes: Long,
        errors: Long,
    )
}
