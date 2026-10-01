package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/** Remembers whether the guided permission onboarding has been completed. */
class OnboardingStore(private val context: Context) {

    private val doneKey = booleanPreferencesKey("onboarding_done")

    val isDone: Flow<Boolean> = context.onboardingDataStore.data.map { it[doneKey] ?: false }

    suspend fun markDone() {
        context.onboardingDataStore.edit { it[doneKey] = true }
    }
}
