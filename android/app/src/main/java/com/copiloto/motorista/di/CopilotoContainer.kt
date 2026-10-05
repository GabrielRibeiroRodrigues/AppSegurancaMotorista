package com.copiloto.motorista.di

import android.content.Context
import com.copiloto.motorista.bluetooth.PanicButtonManager
import com.copiloto.motorista.data.local.CopilotoDatabase
import com.copiloto.motorista.data.model.DemoTrip
import com.copiloto.motorista.data.remote.CopilotoApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.copiloto.motorista.data.remote.NetworkModule
import com.copiloto.motorista.data.repository.AuthRepository
import com.copiloto.motorista.data.repository.PanicRepository
import com.copiloto.motorista.data.repository.RideHistoryRepository
import com.copiloto.motorista.data.settings.ConsentStore
import com.copiloto.motorista.data.settings.DriverProfileRepository
import com.copiloto.motorista.data.settings.MonitoredAppsStore
import com.copiloto.motorista.data.settings.OnboardingStore
import com.copiloto.motorista.data.settings.PanicButtonStore
import com.copiloto.motorista.data.settings.PanicStore
import com.copiloto.motorista.data.settings.RiskZoneRepository
import com.copiloto.motorista.data.settings.TokenStore

/**
 * Lightweight manual dependency container. Avoids an annotation-processing DI
 * framework while still giving services, workers and view models a single wired
 * graph reachable through [com.copiloto.motorista.CopilotoApp.container].
 */
class CopilotoContainer(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore: TokenStore by lazy { TokenStore(appContext) }

    val api: CopilotoApi by lazy { NetworkModule.createApi(tokenStore) }

    private val database: CopilotoDatabase by lazy { CopilotoDatabase.get(appContext) }

    val driverProfileRepository: DriverProfileRepository by lazy {
        DriverProfileRepository(appContext)
    }

    val onboardingStore: OnboardingStore by lazy { OnboardingStore(appContext) }

    val consentStore: ConsentStore by lazy { ConsentStore(appContext) }

    val riskZoneRepository: RiskZoneRepository by lazy { RiskZoneRepository(appContext) }

    val monitoredAppsStore: MonitoredAppsStore by lazy { MonitoredAppsStore(appContext) }

    val panicStore: PanicStore by lazy { PanicStore(appContext) }

    val panicButtonStore: PanicButtonStore by lazy { PanicButtonStore(appContext) }

    /** Single BLE engine shared by the pairing UI and the PanicButtonService. */
    val panicButtonManager: PanicButtonManager by lazy { PanicButtonManager(appContext) }

    /** App-wide signal fired on every panic alert (origin string), so the in-trip
     *  demo screen can confirm a trigger from voice, button or the simulate buttons. */
    val alertEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Set when a simulated ride is accepted; the UI then opens the in-trip demo screen. */
    val demoTripHolder = MutableStateFlow<DemoTrip?>(null)

    val panicRepository: PanicRepository by lazy {
        PanicRepository(api, database.pendingAlertDao(), appContext, alertEvents)
    }

    val rideHistoryRepository: RideHistoryRepository by lazy {
        RideHistoryRepository(database.rideHistoryDao(), api)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(api, tokenStore)
    }
}
