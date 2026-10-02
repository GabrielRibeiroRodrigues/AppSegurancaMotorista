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

private val Context.panicDataStore: DataStore<Preferences> by preferencesDataStore(name = "panic")

/**
 * Safety-mode settings (DesafioMaker): the configurable trigger phrase and whether
 * protection (background voice listening) is enabled.
 */
class PanicStore(private val context: Context) {

    private object Keys {
        val triggerPhrase = stringPreferencesKey("trigger_phrase")
        val protectionEnabled = booleanPreferencesKey("protection_enabled")
    }

    val triggerPhrase: Flow<String> = context.panicDataStore.data.map {
        it[Keys.triggerPhrase] ?: DEFAULT_PHRASE
    }

    val protectionEnabled: Flow<Boolean> = context.panicDataStore.data.map {
        it[Keys.protectionEnabled] ?: false
    }

    suspend fun currentPhrase(): String = triggerPhrase.first()

    suspend fun setPhrase(phrase: String) {
        val clean = phrase.trim().ifEmpty { DEFAULT_PHRASE }
        context.panicDataStore.edit { it[Keys.triggerPhrase] = clean }
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.panicDataStore.edit { it[Keys.protectionEnabled] = enabled }
    }

    companion object {
        const val DEFAULT_PHRASE = "ativar proteção"
    }
}
