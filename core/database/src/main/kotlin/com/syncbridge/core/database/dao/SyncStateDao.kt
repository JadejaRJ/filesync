package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syncbridge.core.database.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Long): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE profileId = :profileId AND relativePath = :relativePath")
    suspend fun delete(profileId: Long, relativePath: String)

    @Query("DELETE FROM sync_state WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM sync_state WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int
}
