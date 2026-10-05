package com.copiloto.motorista.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.bluetooth.BleConnectionState
import com.copiloto.motorista.bluetooth.ScannedDevice
import com.copiloto.motorista.data.settings.PanicButtonConfig
import com.copiloto.motorista.service.PanicButtonService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the ESP32 panic-button pairing/status screen. */
class PanicButtonViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as CopilotoApp).container
    private val manager = container.panicButtonManager
    private val store = container.panicButtonStore

    val connectionState: StateFlow<BleConnectionState> = manager.connectionState
    val scanResults: StateFlow<List<ScannedDevice>> = manager.scanResults
    /** Fires whenever a physical press is received (used by the on-screen test). */
    val panicEvents: SharedFlow<Unit> = manager.panicEvents

    val config: StateFlow<PanicButtonConfig> = store.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanicButtonConfig())

    fun isBleSupported(): Boolean = manager.isBleSupported()
    fun isBluetoothOn(): Boolean = manager.isBluetoothOn()
    fun hasConnectPermission(): Boolean = manager.hasConnectPermission()
    fun hasScanPermission(): Boolean = manager.hasScanPermission()

    fun startScan() = manager.startScan()
    fun stopScan() = manager.stopScan()

    /** Saves the device, arms monitoring and starts the background connection. */
    fun pairAndArm(device: ScannedDevice) {
        manager.stopScan()
        viewModelScope.launch {
            store.pair(device.address, device.name)
            PanicButtonService.start(getApplication())
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) PanicButtonService.start(app) else PanicButtonService.stop(app)
        }
    }

    fun unpair() {
        val app = getApplication<Application>()
        PanicButtonService.stop(app)
        viewModelScope.launch { store.clear() }
    }

    /** Enters/leaves the on-screen test: physical presses won't raise a real alert. */
    fun setTestMode(enabled: Boolean) {
        PanicButtonService.testMode = enabled
    }

    override fun onCleared() {
        super.onCleared()
        manager.stopScan()
        PanicButtonService.testMode = false
    }
}
