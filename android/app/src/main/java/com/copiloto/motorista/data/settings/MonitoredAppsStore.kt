package com.copiloto.motorista.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.copiloto.motorista.data.model.MonitoredApp
import com.copiloto.motorista.data.model.RideSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.monitoredAppsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "monitored_apps")

/**
 * Stores the extra rideshare/delivery apps the driver chose to monitor, on top of
 * the built-in ones ([RideSource.BUILT_INS]). This lets the Copiloto read offers
 * from regional apps it doesn't know at build time (e.g. "Uby Muzambinho").
 *
 * Each entry keeps the package name and the human label so the overlay and history
 * can show the real app name. Entries are serialized as `package␟label`.
 */
class MonitoredAppsStore(private val context: Context) {

    private val extrasKey = stringSetPreferencesKey("extra_apps")

    /** The driver-added apps, sorted by label. */
    val extraApps: Flow<List<MonitoredApp>> = context.monitoredAppsDataStore.data.map { prefs ->
        prefs[extrasKey].orEmpty().mapNotNull(::decode).sortedBy { it.label.lowercase() }
    }

    /**
     * Every package the service should read, mapped to the label to display.
     * Built-ins use their [RideSource] display name; extras use the chosen label.
     */
    val monitoredLabels: Flow<Map<String, String>> = extraApps.map { extras ->
        buildMap {
            RideSource.BUILT_INS.forEach { put(it.packageName!!, it.displayName) }
            extras.forEach { put(it.packageName, it.label) }
        }
    }

    suspend fun currentLabels(): Map<String, String> = monitoredLabels.first()

    suspend fun add(app: MonitoredApp) {
        if (app.packageName.isBlank()) return
        context.monitoredAppsDataStore.edit { prefs ->
            val kept = prefs[extrasKey].orEmpty().filterNot { decode(it)?.packageName == app.packageName }
            prefs[extrasKey] = (kept + encode(app)).toSet()
        }
    }

    suspend fun remove(packageName: String) {
        context.monitoredAppsDataStore.edit { prefs ->
            prefs[extrasKey] = prefs[extrasKey].orEmpty()
                .filterNot { decode(it)?.packageName == packageName }
                .toSet()
        }
    }

    private fun encode(app: MonitoredApp): String = "${app.packageName}$SEP${app.label}"

    private fun decode(raw: String): MonitoredApp? {
        val i = raw.indexOf(SEP)
        if (i <= 0) return null
        return MonitoredApp(raw.substring(0, i), raw.substring(i + 1))
    }

    private companion object {
        const val SEP = '\u001F'
    }
}
