package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.syncbridge.core.database.entity.SyncErrorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncErrorDao {
    @Query("SELECT * FROM sync_errors WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<SyncErrorEntity>>

    @Query("SELECT * FROM sync_errors WHERE runId = :runId ORDER BY createdAt DESC")
    suspend fun getForRun(runId: Long): List<SyncErrorEntity>

    @Insert
    suspend fun insert(entity: SyncErrorEntity): Long

    @Query("DELETE FROM sync_errors WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_errors WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)
}
