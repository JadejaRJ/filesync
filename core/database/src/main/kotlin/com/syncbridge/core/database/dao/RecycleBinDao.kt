package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.syncbridge.core.database.entity.RecycleBinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM recycle_bin WHERE profileId = :profileId ORDER BY deletedAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<RecycleBinEntity>>

    @Insert
    suspend fun insert(entity: RecycleBinEntity): Long

    @Delete
    suspend fun delete(entity: RecycleBinEntity)
}
