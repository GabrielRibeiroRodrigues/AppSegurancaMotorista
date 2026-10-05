package com.copiloto.motorista.data.model

/**
 * A simulated trip shown on the in-trip demo screen after the driver accepts a
 * simulated offer. Carries just what the screen needs to render the header; the
 * map route is a fixed path through Muzambinho (see `MuzambinhoRoute`).
 */
data class DemoTrip(
    val sourceLabel: String,
    val grossPrice: Double,
    val distanceKm: Double,
    val timeMinutes: Int,
    val pickup: String,
    val dropoff: String,
)
