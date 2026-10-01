package com.copiloto.motorista.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.copiloto.motorista.CopilotoApp

/**
 * Pushes unsynced ride history to the backend (Module F). Scheduled by
 * [SyncScheduler] to run only when the network is available; retries on failure.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CopilotoApp).container
        // Only attempt a sync when the driver is signed in; otherwise retry later.
        val loggedIn = container.tokenStore.refreshToken() != null
        if (!loggedIn) return Result.success()
        return try {
            container.rideHistoryRepository.syncPending()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
