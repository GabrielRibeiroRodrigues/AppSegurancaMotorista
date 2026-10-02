package com.copiloto.motorista.data.repository

import android.content.Context
import com.copiloto.motorista.data.local.PendingAlertDao
import com.copiloto.motorista.data.local.PendingAlertEntity
import com.copiloto.motorista.data.remote.CopilotoApi
import com.copiloto.motorista.data.remote.dto.CreateAlertRequest
import com.copiloto.motorista.service.LocationHelper
import com.copiloto.motorista.sync.AlertSyncScheduler
import java.time.Instant

/**
 * Fires panic alerts (DesafioMaker). Sends immediately; on failure the alert is
 * queued locally and retried by [com.copiloto.motorista.sync.AlertSyncWorker]
 * (offline-first), so a real emergency is never lost to a flaky network.
 */
class PanicRepository(
    private val api: CopilotoApi,
    private val pendingAlertDao: PendingAlertDao,
    private val appContext: Context,
) {

    /** Fires an alert from the current location. Returns true if sent right away. */
    suspend fun fireAlert(transcript: String, isTest: Boolean): Boolean {
        val (lat, lng) = LocationHelper.lastKnown(appContext)
        val request = CreateAlertRequest(
            timestamp = Instant.now().toString(),
            lat = lat,
            lng = lng,
            transcript = transcript,
            isTest = isTest,
        )
        return try {
            api.createAlert(request)
            true
        } catch (e: Exception) {
            pendingAlertDao.insert(
                PendingAlertEntity(
                    timestamp = request.timestamp,
                    lat = lat,
                    lng = lng,
                    transcript = transcript,
                    isTest = isTest,
                ),
            )
            AlertSyncScheduler.schedule(appContext)
            false
        }
    }

    /** Resends every queued alert. Throws on failure so the worker can retry. */
    suspend fun syncPending(): Int {
        val pending = pendingAlertDao.getAll()
        var count = 0
        for (alert in pending) {
            api.createAlert(
                CreateAlertRequest(
                    timestamp = alert.timestamp,
                    lat = alert.lat,
                    lng = alert.lng,
                    transcript = alert.transcript,
                    isTest = alert.isTest,
                ),
            )
            pendingAlertDao.delete(alert)
            count++
        }
        return count
    }

    suspend fun pendingCount(): Int = pendingAlertDao.count()
}
