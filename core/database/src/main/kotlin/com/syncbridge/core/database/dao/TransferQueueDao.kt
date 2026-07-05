package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.syncbridge.core.database.entity.TransferQueueEntity

@Dao
interface TransferQueueDao {
    @Query("SELECT * FROM transfer_queue WHERE profileId = :profileId ORDER BY priority DESC, createdAt ASC")
    suspend fun getForProfile(profileId: Long): List<TransferQueueEntity>

    @Query("SELECT * FROM transfer_queue WHERE status = :status")
    suspend fun getByStatus(status: String): List<TransferQueueEntity>

    @Insert
    suspend fun insert(entity: TransferQueueEntity): Long

    @Update
    suspend fun update(entity: TransferQueueEntity)

    @Delete
    suspend fun delete(entity: TransferQueueEntity)

    @Query("DELETE FROM transfer_queue WHERE profileId = :profileId AND status = 'FAILED'")
    suspend fun clearFailedForProfile(profileId: Long)
}
