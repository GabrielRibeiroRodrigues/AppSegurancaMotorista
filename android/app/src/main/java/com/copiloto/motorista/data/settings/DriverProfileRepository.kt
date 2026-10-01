package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.copiloto.motorista.data.model.DriverProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_profile")

/**
 * Stores the driver's configuration (Module F) locally and reactively. The
 * profile is small and single-instance, so DataStore fits better than Room here.
 */
class DriverProfileRepository(private val context: Context) {

    private object Keys {
        val fuelPrice = doublePreferencesKey("fuel_price_per_liter")
        val kmPerLiter = doublePreferencesKey("km_per_liter")
        val maintenance = doublePreferencesKey("maintenance_cost_per_km")
        val target = doublePreferencesKey("target_per_km")
        val minimum = doublePreferencesKey("minimum_per_km")
        val targetHour = doublePreferencesKey("target_per_hour")
        val dailyGoal = doublePreferencesKey("daily_goal")
        val voice = booleanPreferencesKey("voice_enabled")
    }

    val profile: Flow<DriverProfile> = context.dataStore.data.map { prefs ->
        val defaults = DriverProfile()
        DriverProfile(
            fuelPricePerLiter = prefs[Keys.fuelPrice] ?: defaults.fuelPricePerLiter,
            kmPerLiter = prefs[Keys.kmPerLiter] ?: defaults.kmPerLiter,
            maintenanceCostPerKm = prefs[Keys.maintenance] ?: defaults.maintenanceCostPerKm,
            targetPerKm = prefs[Keys.target] ?: defaults.targetPerKm,
            minimumPerKm = prefs[Keys.minimum] ?: defaults.minimumPerKm,
            targetPerHour = prefs[Keys.targetHour] ?: defaults.targetPerHour,
            dailyGoal = prefs[Keys.dailyGoal] ?: defaults.dailyGoal,
            voiceEnabled = prefs[Keys.voice] ?: defaults.voiceEnabled,
        )
    }

    suspend fun current(): DriverProfile = profile.first()

    suspend fun save(profile: DriverProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.fuelPrice] = profile.fuelPricePerLiter
            prefs[Keys.kmPerLiter] = profile.kmPerLiter
            prefs[Keys.maintenance] = profile.maintenanceCostPerKm
            prefs[Keys.target] = profile.targetPerKm
            prefs[Keys.minimum] = profile.minimumPerKm
            prefs[Keys.targetHour] = profile.targetPerHour
            prefs[Keys.dailyGoal] = profile.dailyGoal
            prefs[Keys.voice] = profile.voiceEnabled
        }
    }
}
