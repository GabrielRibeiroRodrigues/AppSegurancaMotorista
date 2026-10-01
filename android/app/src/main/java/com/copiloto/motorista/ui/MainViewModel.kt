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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as CopilotoApp).container

    val profile: StateFlow<DriverProfile> = container.driverProfileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverProfile())

    val history: StateFlow<List<RideHistoryEntity>> =
        container.rideHistoryRepository.observeRecent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        return RideOffer(
            source = source,
            grossPrice = price,
            distanceKm = (distance * 10).toInt() / 10.0,
            timeMinutes = minutes,
        )
    }
}
