package com.syncbridge.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syncbridge.core.database.entity.ConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY name ASC")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getById(id: Long): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ConnectionEntity): Long

    @Update
    suspend fun update(entity: ConnectionEntity)

    @Delete
    suspend fun delete(entity: ConnectionEntity)

    @Query("UPDATE connections SET lastTestStatus = :status, lastTestTime = :time WHERE id = :id")
    suspend fun updateLastTestResult(id: Long, status: String, time: Long)

    @Query("UPDATE connections SET lastSuccessfulLoginAt = :time WHERE id = :id")
    suspend fun updateLastSuccessfulLogin(id: Long, time: Long)
}
