package com.copiloto.motorista.di

import android.content.Context
import com.copiloto.motorista.data.local.CopilotoDatabase
import com.copiloto.motorista.data.remote.CopilotoApi
import com.copiloto.motorista.data.remote.NetworkModule
import com.copiloto.motorista.data.repository.RideHistoryRepository
import com.copiloto.motorista.data.settings.DriverProfileRepository

/**
 * Lightweight manual dependency container. Avoids an annotation-processing DI
 * framework while still giving services, workers and view models a single wired
 * graph reachable through [com.copiloto.motorista.CopilotoApp.container].
 */
class CopilotoContainer(context: Context) {

    private val appContext = context.applicationContext

    val api: CopilotoApi by lazy { NetworkModule.createApi() }

    private val database: CopilotoDatabase by lazy { CopilotoDatabase.get(appContext) }

    val driverProfileRepository: DriverProfileRepository by lazy {
        DriverProfileRepository(appContext)
    }

    val rideHistoryRepository: RideHistoryRepository by lazy {
        RideHistoryRepository(database.rideHistoryDao(), api)
    }
}
