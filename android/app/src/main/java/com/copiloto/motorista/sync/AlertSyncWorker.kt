package com.copiloto.motorista.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.copiloto.motorista.CopilotoApp
import java.time.Duration

/** Retries queued panic alerts when the network returns (DesafioMaker). */
class AlertSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CopilotoApp).container
        if (container.tokenStore.refreshToken() == null) return Result.success()
        return try {
            container.panicRepository.syncPending()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}

/** Schedules the retry of queued alerts as soon as connectivity allows. */
object AlertSyncScheduler {
    private const val WORK = "copiloto_alert_sync"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlertSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofSeconds(15))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
