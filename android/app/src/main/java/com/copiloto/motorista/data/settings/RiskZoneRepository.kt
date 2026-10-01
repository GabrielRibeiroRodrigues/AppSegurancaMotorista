package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.riskDataStore: DataStore<Preferences> by preferencesDataStore(name = "risk_zones")

/**
 * Stores the driver's risk-zone keywords (Funcionalidade 1 — Blacklist de bairros).
 * A ride whose on-screen text contains any of these words is flagged as a risk area.
 */
class RiskZoneRepository(private val context: Context) {

    private val keywordsKey = stringSetPreferencesKey("blacklist_keywords")

    val keywords: Flow<List<String>> = context.riskDataStore.data.map { prefs ->
        prefs[keywordsKey].orEmpty().sorted()
    }

    suspend fun current(): List<String> = keywords.first()

    suspend fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.riskDataStore.edit { prefs ->
            val current = prefs[keywordsKey].orEmpty()
            // Case-insensitive de-dup so the same neighborhood isn't added twice.
            if (current.none { it.equals(trimmed, ignoreCase = true) }) {
                prefs[keywordsKey] = current + trimmed
            }
        }
    }

    suspend fun remove(keyword: String) {
        context.riskDataStore.edit { prefs ->
            prefs[keywordsKey] = prefs[keywordsKey].orEmpty()
                .filterNot { it.equals(keyword, ignoreCase = true) }
                .toSet()
        }
    }
}
