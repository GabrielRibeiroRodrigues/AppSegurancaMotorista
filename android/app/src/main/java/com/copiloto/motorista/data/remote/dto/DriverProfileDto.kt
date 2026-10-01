package com.copiloto.motorista.data.remote.dto

import com.squareup.moshi.Json

/** Wire representation of the driver's configuration (Module F). */
data class DriverProfileDto(
    @Json(name = "fuel_price_per_liter") val fuelPricePerLiter: Double,
    @Json(name = "km_per_liter") val kmPerLiter: Double,
    @Json(name = "maintenance_cost_per_km") val maintenanceCostPerKm: Double,
    @Json(name = "target_per_km") val targetPerKm: Double,
    @Json(name = "minimum_per_km") val minimumPerKm: Double,
    @Json(name = "voice_enabled") val voiceEnabled: Boolean,
)
