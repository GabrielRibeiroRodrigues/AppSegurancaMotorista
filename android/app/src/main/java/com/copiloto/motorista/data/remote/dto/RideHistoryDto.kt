package com.copiloto.motorista.data.remote.dto

import com.squareup.moshi.Json

/** Wire representation of a ride history row (Module F). */
data class RideHistoryDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "source") val source: String,
    @Json(name = "gross_price") val grossPrice: Double,
    @Json(name = "distance_km") val distanceKm: Double,
    @Json(name = "time_minutes") val timeMinutes: Int,
    @Json(name = "net_profit") val netProfit: Double,
    @Json(name = "gross_per_km") val grossPerKm: Double,
    @Json(name = "gross_per_hour") val grossPerHour: Double,
    @Json(name = "classification") val classification: String,
    @Json(name = "accepted") val accepted: Boolean,
    @Json(name = "captured_at") val capturedAt: String,
)
