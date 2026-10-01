package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.local.RideHistoryDao
import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.RideEvaluation
import com.copiloto.motorista.data.remote.CopilotoApi
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for ride history: writes go to Room first (offline-first),
 * and [syncPending] later pushes unsynced rows to the Django backend (Module F).
 */
class RideHistoryRepository(
    private val dao: RideHistoryDao,
    private val api: CopilotoApi,
) {

    fun observeHistory(): Flow<List<RideHistoryEntity>> = dao.observeAll()

    fun observeRecent(limit: Int = 20): Flow<List<RideHistoryEntity>> = dao.observeRecent(limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun record(evaluation: RideEvaluation, accepted: Boolean = false): Long =
        dao.insert(evaluation.toEntity(accepted))

    suspend fun setAccepted(localId: Long, accepted: Boolean) =
        dao.updateAccepted(localId, accepted)

    /**
     * Pushes every unsynced row to the backend (authenticated via JWT). Returns the
     * number of rows synced. Throws on network/server failure so WorkManager can retry.
     */
    suspend fun syncPending(): Int {
        val pending = dao.getUnsynced()
        var count = 0
        for (row in pending) {
            val response = api.createRide(row.toDto())
            dao.markSynced(row.id, response.id)
            count++
        }
        return count
    }
}
