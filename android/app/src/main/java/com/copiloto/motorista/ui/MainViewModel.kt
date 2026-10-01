package com.copiloto.motorista.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.data.repository.toDto
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.sync.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as CopilotoApp).container

    init {
        // Back up any rides left unsynced (e.g. the app was closed before the card resolved).
        SyncScheduler.syncNow(application)
    }

    val profile: StateFlow<DriverProfile> = container.driverProfileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverProfile())

    val history: StateFlow<List<RideHistoryEntity>> =
        container.rideHistoryRepository.observeRecent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sum of net profit from rides accepted today (daily-goal tracker). */
    val todayAcceptedProfit: StateFlow<Double> =
        container.rideHistoryRepository.observeTodayAcceptedProfit()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** null while loading; false shows the onboarding, true shows the main app. */
    val onboardingDone: StateFlow<Boolean?> = container.onboardingStore.isDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markOnboardingDone() {
        viewModelScope.launch { container.onboardingStore.markDone() }
    }

    val blacklist: StateFlow<List<String>> = container.riskZoneRepository.keywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addKeyword(keyword: String) {
        viewModelScope.launch { container.riskZoneRepository.add(keyword) }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch { container.riskZoneRepository.remove(keyword) }
    }

    fun saveProfile(profile: DriverProfile) {
        viewModelScope.launch {
            container.driverProfileRepository.save(profile)
            // Mirror config to the backend opportunistically; ignore offline/auth failures.
            runCatching { container.api.updateProfile(profile.toDto()) }
        }
    }

    /**
     * Ride Simulator (Module G): injects a mock offer straight into the overlay +
     * voice pipeline, exactly as a scraped ride would, without the AccessibilityService.
     */
    fun simulateRide() {
        OverlayService.showOffer(getApplication(), randomOffer())
    }

    private fun randomOffer(): RideOffer {
        val source = listOf(RideSource.UBER, RideSource.NINETY_NINE, RideSource.INDRIVE).random()
        val distance = Random.nextDouble(2.0, 14.0)
        val minutes = Random.nextInt(6, 35)
        // Spread prices so the simulator produces green, yellow and red outcomes.
        val perKm = Random.nextDouble(0.9, 2.6)
        val price = (distance * perKm).let { (it * 100).toInt() / 100.0 }
        val neighborhood = NEIGHBORHOODS.random()
        return RideOffer(
            source = source,
            grossPrice = price,
            distanceKm = (distance * 10).toInt() / 10.0,
            timeMinutes = minutes,
            dropoff = neighborhood,
            // Mimics the captured screen text so risk-zone keywords can match.
            rawText = "Destino: $neighborhood",
        )
    }

    private companion object {
        val NEIGHBORHOODS = listOf(
            "Centro", "Jardim das Flores", "Vila Nova", "Parque Industrial",
            "Bairro Alto", "Morro Verde", "Residencial Sol",
        )
    }
}
