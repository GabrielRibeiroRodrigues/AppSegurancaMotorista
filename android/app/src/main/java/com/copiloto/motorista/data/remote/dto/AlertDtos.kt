package com.copiloto.motorista.data.remote.dto

import com.squareup.moshi.Json

/** Payload the driver app sends when a panic alert fires (DesafioMaker). */
data class CreateAlertRequest(
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double,
    @Json(name = "transcript") val transcript: String,
    @Json(name = "is_test") val isTest: Boolean,
)

/** Minimal response fields we care about after firing. */
data class AlertResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "status") val status: String,
)
