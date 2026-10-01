package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.RideEvaluation
import com.copiloto.motorista.data.remote.dto.RideHistoryDto
import java.time.Instant

/** Converts an engine [RideEvaluation] into a persistable row. */
fun RideEvaluation.toEntity(accepted: Boolean, createdAt: Long = System.currentTimeMillis()) =
    RideHistoryEntity(
        source = offer.source.name,
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
        createdAt = createdAt,
    )

/** Converts a stored row into its wire representation for sync. */
fun RideHistoryEntity.toDto() = RideHistoryDto(
    source = source,
    grossPrice = grossPrice,
    distanceKm = distanceKm,
    timeMinutes = timeMinutes,
    netProfit = netProfit,
    grossPerKm = grossPerKm,
    grossPerHour = grossPerHour,
    classification = classification,
    accepted = accepted,
    capturedAt = Instant.ofEpochMilli(createdAt).toString(),
)
