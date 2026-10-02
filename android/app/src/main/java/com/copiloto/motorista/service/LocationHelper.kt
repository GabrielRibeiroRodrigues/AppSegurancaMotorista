package com.copiloto.motorista.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Best-effort current location for a panic alert (DesafioMaker). Uses the system
 * [LocationManager]'s last known fix (no Google Play Services dependency) and
 * falls back to Muzambinho-MG when a location isn't available.
 */
object LocationHelper {

    /** IFSULDEMINAS — Campus Muzambinho, used when no real fix is available. */
    val FALLBACK = -21.3763 to -46.5262

    fun lastKnown(context: Context): Pair<Double, Double> {
        if (!hasLocationPermission(context)) return FALLBACK
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return FALLBACK
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            val location = runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)
                } else {
                    null
                }
            }.getOrNull()
            if (location != null) return location.latitude to location.longitude
        }
        return FALLBACK
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
