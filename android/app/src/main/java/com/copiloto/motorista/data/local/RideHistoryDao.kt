package com.copiloto.motorista.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideHistoryDao {

    @Insert
    suspend fun insert(entity: RideHistoryEntity): Long

    @Query("SELECT * FROM ride_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<RideHistoryEntity>

    @Query("UPDATE ride_history SET synced = 1, remoteId = :remoteId WHERE id = :localId")
    suspend fun markSynced(localId: Long, remoteId: Long?)

    @Query("UPDATE ride_history SET accepted = :accepted WHERE id = :localId")
    suspend fun updateAccepted(localId: Long, accepted: Boolean)

    @Query("SELECT COUNT(*) FROM ride_history")
    fun observeCount(): Flow<Int>
}
