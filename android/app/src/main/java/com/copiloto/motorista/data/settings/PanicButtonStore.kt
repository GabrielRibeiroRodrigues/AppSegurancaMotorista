package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.panicButtonDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "panic_button")

/** The ESP32 panic button the driver paired, and whether it is armed. */
data class PanicButtonConfig(
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val enabled: Boolean = false,
) {
    val isPaired: Boolean get() = !deviceAddress.isNullOrBlank()
}

/**
 * Persists the paired ESP32 panic button (MAC address + friendly name) and whether
 * monitoring is armed. Address + enabled survive reboots so [BootReceiver] can
 * re-arm the connection.
 */
class PanicButtonStore(private val context: Context) {

    private object Keys {
        val address = stringPreferencesKey("device_address")
        val name = stringPreferencesKey("device_name")
        val enabled = booleanPreferencesKey("enabled")
    }

    val config: Flow<PanicButtonConfig> = context.panicButtonDataStore.data.map { prefs ->
        PanicButtonConfig(
            deviceAddress = prefs[Keys.address],
            deviceName = prefs[Keys.name],
            enabled = prefs[Keys.enabled] ?: false,
        )
    }

    suspend fun current(): PanicButtonConfig = config.first()

    /** Saves the paired device and arms monitoring. */
    suspend fun pair(address: String, name: String?) {
        context.panicButtonDataStore.edit { prefs ->
            prefs[Keys.address] = address
            if (name != null) prefs[Keys.name] = name else prefs.remove(Keys.name)
            prefs[Keys.enabled] = true
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.panicButtonDataStore.edit { it[Keys.enabled] = enabled }
    }

    /** Forgets the paired device and disarms. */
    suspend fun clear() {
        context.panicButtonDataStore.edit { prefs ->
            prefs.remove(Keys.address)
            prefs.remove(Keys.name)
            prefs[Keys.enabled] = false
        }
    }
}
