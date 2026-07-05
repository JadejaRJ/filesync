package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.syncbridge.core.database.entity.SyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRunDao {
    @Query("SELECT * FROM sync_runs WHERE profileId = :profileId ORDER BY startedAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<SyncRunEntity>>

    @Query("SELECT * FROM sync_runs WHERE id = :id")
    suspend fun getById(id: Long): SyncRunEntity?

    @Insert
    suspend fun insert(entity: SyncRunEntity): Long

    @Update
    suspend fun update(entity: SyncRunEntity)

    @Query("DELETE FROM sync_runs WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("DELETE FROM sync_runs")
    suspend fun clearAll()
}
