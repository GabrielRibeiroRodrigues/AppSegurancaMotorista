package com.copiloto.motorista.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A captured ride offer and its evaluation, persisted offline-first (Module F).
 * Rows with [synced] = false are pushed to the backend by the sync worker.
 */
@Entity(tableName = "ride_history")
data class RideHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val grossPrice: Double,
    val distanceKm: Double,
    val timeMinutes: Int,
    val costPerKm: Double,
    val totalCost: Double,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val netProfit: Double,
    val classification: String,
    val accepted: Boolean,
    val createdAt: Long,
    val synced: Boolean = false,
    val remoteId: Long? = null,
)
