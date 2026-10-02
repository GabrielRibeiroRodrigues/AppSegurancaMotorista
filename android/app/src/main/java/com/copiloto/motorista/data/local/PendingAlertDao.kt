package com.copiloto.motorista.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingAlertDao {

    @Insert
    suspend fun insert(entity: PendingAlertEntity): Long

    @Query("SELECT * FROM pending_alert ORDER BY id ASC")
    suspend fun getAll(): List<PendingAlertEntity>

    @Delete
    suspend fun delete(entity: PendingAlertEntity)

    @Query("SELECT COUNT(*) FROM pending_alert")
    suspend fun count(): Int
}
