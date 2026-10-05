package com.copiloto.motorista.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A device found while scanning for the panic button. */
data class ScannedDevice(val address: String, val name: String?, val rssi: Int)

enum class BleConnectionState { DISCONNECTED, CONNECTING, CONNECTED, BLUETOOTH_OFF, NO_PERMISSION, UNSUPPORTED }

/**
 * BLE central for the ESP32 panic button: scans to pair, keeps a resilient
 * connection (auto-reconnect), subscribes to the panic characteristic and emits a
 * de-duplicated event on each real press. Single instance held by the DI container
 * and shared by the pairing UI and [com.copiloto.motorista.service.PanicButtonService].
 *
 * Android reality this handles: BLE background works only while a foreground
 * service holds the connection; reconnection uses `autoConnect=true` so the OS
 * re-links when the ESP32 comes back in range; a sequence counter in the payload
 * stops a reconnect from re-firing the last press.
 */
class PanicButtonManager(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _panicEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Emits once per deliberate, de-duplicated button press. */
    val panicEvents: SharedFlow<Unit> = _panicEvents.asSharedFlow()

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var desiredAddress: String? = null
    private var lastSeq: Long = -1L
    private var scanning = false

    // --- Capability / permission checks -----------------------------------

    fun isBleSupported(): Boolean =
        adapter != null && appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }

    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Pre-31, scanning requires location.
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    // --- Scanning (pairing) -----------------------------------------------

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        if (!isBleSupported() || !isBluetoothOn() || !hasScanPermission()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        _scanResults.value = emptyList()
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(PanicButtonBle.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }
            .onFailure { scanning = false }
        // Safety cap so the radio isn't left scanning.
        handler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val name = runCatching { nameOf(device) }.getOrNull() ?: result.scanRecord?.deviceName
            val entry = ScannedDevice(address, name, result.rssi)
            _scanResults.value = (_scanResults.value.filterNot { it.address == address } + entry)
                .sortedByDescending { it.rssi }
        }
    }

    @SuppressLint("MissingPermission")
    private fun nameOf(device: android.bluetooth.BluetoothDevice): String? =
        if (hasConnectPermission()) device.name else null

    // --- Connection (always-on, driven by the service) --------------------

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!isBleSupported()) { _connectionState.value = BleConnectionState.UNSUPPORTED; return }
        if (!isBluetoothOn()) { _connectionState.value = BleConnectionState.BLUETOOTH_OFF; return }
        if (!hasConnectPermission()) { _connectionState.value = BleConnectionState.NO_PERMISSION; return }

        desiredAddress = address
        lastSeq = -1L
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }
        closeGatt()
        _connectionState.value = BleConnectionState.CONNECTING
        // autoConnect=true → the OS reconnects automatically when the ESP32 returns.
        gatt = runCatching {
            device.connectGatt(appContext, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        desiredAddress = null
        closeGatt()
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.CONNECTING // until services are ready
                    runCatching { g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    // Re-arm: with autoConnect the OS keeps trying while we still want it.
                    if (desiredAddress != null && g == gatt) {
                        handler.postDelayed({
                            if (desiredAddress != null) runCatching { g.connect() }
                        }, RECONNECT_DELAY_MS)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(PanicButtonBle.SERVICE_UUID)
                ?.getCharacteristic(PanicButtonBle.PANIC_CHAR_UUID)
            if (characteristic == null) {
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }
            runCatching { g.setCharacteristicNotification(characteristic, true) }
            val cccd = characteristic.getDescriptor(PanicButtonBle.CCCD_UUID)
            if (cccd != null) {
                enableNotifications(g, cccd)
            }
            _connectionState.value = BleConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handlePayload(characteristic, value)
        }

        @Deprecated("Deprecated in API 33; kept for API < 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handlePayload(characteristic, characteristic.value ?: ByteArray(0))
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, cccd: BluetoothGattDescriptor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            runCatching { g.writeDescriptor(cccd) }
        }
    }

    private fun handlePayload(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != PanicButtonBle.PANIC_CHAR_UUID) return
        if (!PanicButtonBle.isPanic(value)) return
        // Dedupe replays on reconnect via the payload's sequence number.
        val seq = PanicButtonBle.sequence(value)
        if (seq == lastSeq) return
        lastSeq = seq
        _panicEvents.tryEmit(Unit)
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val RECONNECT_DELAY_MS = 2_000L
    }
}
