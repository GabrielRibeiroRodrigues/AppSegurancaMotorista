package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Persists the JWT access/refresh tokens (Module 3 — auth). Kept separate from the
 * driver profile store so signing out can clear credentials independently.
 */
class TokenStore(private val context: Context) {

    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
    }

    /** Emits whether the user currently has a refresh token (i.e. is signed in). */
    val isLoggedIn: Flow<Boolean> =
        context.authDataStore.data.map { it[Keys.refresh] != null }

    suspend fun accessToken(): String? =
        context.authDataStore.data.map { it[Keys.access] }.first()

    suspend fun refreshToken(): String? =
        context.authDataStore.data.map { it[Keys.refresh] }.first()

    suspend fun save(access: String, refresh: String) {
        context.authDataStore.edit {
            it[Keys.access] = access
            it[Keys.refresh] = refresh
        }
    }

    suspend fun updateAccess(access: String) {
        context.authDataStore.edit { it[Keys.access] = access }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
