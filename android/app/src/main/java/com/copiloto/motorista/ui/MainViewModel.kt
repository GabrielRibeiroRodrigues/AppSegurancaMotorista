package com.copiloto.motorista.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.DemoTrip
import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.model.MonitoredApp
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.data.repository.AlertOrigin
import com.copiloto.motorista.data.repository.toDto
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.service.PanicService
import com.copiloto.motorista.service.StreamingService
import com.copiloto.motorista.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // --- Monitored apps (which rideshare/delivery apps the Copiloto reads) ---

    /** Apps the driver added on top of the built-in Uber/99/inDrive. */
    val monitoredApps: StateFlow<List<MonitoredApp>> = container.monitoredAppsStore.extraApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMonitoredApp(app: MonitoredApp) {
        viewModelScope.launch { container.monitoredAppsStore.add(app) }
    }

    fun removeMonitoredApp(packageName: String) {
        viewModelScope.launch { container.monitoredAppsStore.remove(packageName) }
    }

    /** Lists the installed launchable apps (off the main thread) for the picker. */
    fun loadInstalledApps(onResult: (List<MonitoredApp>) -> Unit) {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val self = getApplication<Application>().packageName
                runCatching {
                    pm.queryIntentActivities(intent, 0)
                        .map { MonitoredApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                        .filter { it.packageName != self }
                        .distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            onResult(apps)
        }
    }

    // --- Protection mode (DesafioMaker — panic) ---

    val protectionEnabled: StateFlow<Boolean> = container.panicStore.protectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val triggerPhrase: StateFlow<String> = container.panicStore.triggerPhrase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Turns protection on/off: persists the choice and starts/stops the listener. */
    fun setProtection(enabled: Boolean) {
        viewModelScope.launch { container.panicStore.setProtectionEnabled(enabled) }
        val app = getApplication<Application>()
        if (enabled) PanicService.start(app) else PanicService.stop(app)
    }

    fun setTriggerPhrase(phrase: String) {
        viewModelScope.launch { container.panicStore.setPhrase(phrase) }
    }

    /** Fires a test alert (shown as a test in the Central de Operações) and
     *  streams the camera so the operator can verify the live video too. */
    fun sendTestAlert(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val alertId = runCatching {
                container.panicRepository.fireAlert(
                    "Alerta de teste",
                    isTest = true,
                    origin = AlertOrigin.TESTE,
                )
            }.getOrNull()
            if (alertId != null) StreamingService.start(getApplication(), alertId)
            onResult(alertId != null)
        }
    }

    fun saveProfile(profile: DriverProfile) {
        viewModelScope.launch {
            container.driverProfileRepository.save(profile)
            // Mirror config to the backend opportunistically; ignore offline/auth failures.
            runCatching { container.api.updateProfile(profile.toDto()) }
        }
    }

    // --- In-trip demo (map + panic demonstration) ---

    /** Set when a simulated ride is accepted → the UI opens the in-trip demo screen. */
    val demoTrip: StateFlow<DemoTrip?> = container.demoTripHolder.asStateFlow()

    /** Fires once per alert (origin), so the in-trip screen can confirm any trigger. */
    val alertEvents: SharedFlow<String> = container.alertEvents

    fun clearDemoTrip() {
        container.demoTripHolder.value = null
    }

    /** Backup trigger for the demo: fires a real alert as if from voice/button. */
    fun firePanic(origin: String) {
        viewModelScope.launch {
            val alertId = runCatching {
                container.panicRepository.fireAlert("Demonstração", isTest = false, origin = origin)
            }.getOrNull()
            if (alertId != null) StreamingService.start(getApplication(), alertId)
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
            isDemo = true,
        )
    }

    private companion object {
        val NEIGHBORHOODS = listOf(
            "Centro", "Jardim das Flores", "Vila Nova", "Parque Industrial",
            "Bairro Alto", "Morro Verde", "Residencial Sol",
        )
    }
}
