package com.copiloto.motorista.data.repository

import android.content.Context
import com.copiloto.motorista.data.local.PendingAlertDao
import com.copiloto.motorista.data.local.PendingAlertEntity
import com.copiloto.motorista.data.remote.CopilotoApi
import com.copiloto.motorista.data.remote.dto.CreateAlertRequest
import com.copiloto.motorista.service.LocationHelper
import com.copiloto.motorista.sync.AlertSyncScheduler
import java.time.Instant

/** How a panic alert was triggered (mirrors the backend `PanicAlert.Origin`). */
object AlertOrigin {
    const val APP = "APP"
    const val VOZ = "VOZ"
    const val BOTAO_PANICO = "BOTAO_PANICO"
    const val TESTE = "TESTE"
}

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

    /**
     * Fires an alert from the current location. Returns the created alert id when
     * it was sent right away (so the caller can start the live video for that id),
     * or null when it was queued offline. [origin] records how it was triggered
     * (voice, physical button, …) — see [AlertOrigin].
     */
    suspend fun fireAlert(
        transcript: String,
        isTest: Boolean,
        origin: String = AlertOrigin.APP,
    ): Long? {
        val (lat, lng) = LocationHelper.lastKnown(appContext)
        val request = CreateAlertRequest(
            timestamp = Instant.now().toString(),
            lat = lat,
            lng = lng,
            transcript = transcript,
            origin = origin,
            isTest = isTest,
        )
        return try {
            api.createAlert(request).id
        } catch (e: Exception) {
            pendingAlertDao.insert(
                PendingAlertEntity(
                    timestamp = request.timestamp,
                    lat = lat,
                    lng = lng,
                    transcript = transcript,
                    isTest = isTest,
                    origin = origin,
                ),
            )
            AlertSyncScheduler.schedule(appContext)
            null
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
                    origin = alert.origin,
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
