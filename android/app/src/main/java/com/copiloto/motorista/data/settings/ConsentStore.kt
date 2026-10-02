package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(name = "consent")

/**
 * Records the driver's explicit consent (LGPD) to the app using camera, microphone,
 * location and on-screen ride data. No feature that touches that data runs until the
 * driver accepts, so consent gates the whole app at launch.
 */
class ConsentStore(private val context: Context) {

    private val acceptedKey = booleanPreferencesKey("privacy_accepted")

    val accepted: Flow<Boolean> = context.consentDataStore.data.map { it[acceptedKey] ?: false }

    suspend fun accept() {
        context.consentDataStore.edit { it[acceptedKey] = true }
    }
}
