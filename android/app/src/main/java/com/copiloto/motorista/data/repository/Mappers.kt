package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.RideEvaluation
import com.copiloto.motorista.data.remote.dto.RideHistoryDto
import java.time.Instant

/** Converts an engine [RideEvaluation] into a persistable row. */
fun RideEvaluation.toEntity(accepted: Boolean, createdAt: Long = System.currentTimeMillis()) =
    RideHistoryEntity(
        // Store the custom app label when present so history shows the real name;
        // sourceName() in the UI falls back to the raw string for non-enum values.
        source = offer.sourceLabel?.takeIf { it.isNotBlank() } ?: offer.source.name,
        grossPrice = offer.grossPrice,
        distanceKm = offer.distanceKm,
        timeMinutes = offer.timeMinutes,
        costPerKm = costPerKm,
        totalCost = totalCost,
        grossPerKm = grossPerKm,
        grossPerHour = grossPerHour,
        netProfit = netProfit,
        classification = classification.name,
        accepted = accepted,
        pickup = offer.pickup.orEmpty(),
        dropoff = offer.dropoff.orEmpty(),
        createdAt = createdAt,
    )

/** Converts a stored row into its wire representation for sync.
 *  Financial/distance values are rounded to 2 decimals to satisfy the backend
 *  DecimalField precision (see [round2]). */
fun RideHistoryEntity.toDto() = RideHistoryDto(
    source = source,
    grossPrice = grossPrice.round2(),
    distanceKm = distanceKm.round2(),
    timeMinutes = timeMinutes,
    netProfit = netProfit.round2(),
    grossPerKm = grossPerKm.round2(),
    grossPerHour = grossPerHour.round2(),
    classification = classification,
    accepted = accepted,
    pickup = pickup,
    dropoff = dropoff,
    capturedAt = Instant.ofEpochMilli(createdAt).toString(),
)
