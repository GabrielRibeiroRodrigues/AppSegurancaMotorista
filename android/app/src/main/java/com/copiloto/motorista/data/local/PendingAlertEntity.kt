package com.copiloto.motorista.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A panic alert queued locally when the network was unavailable (offline-first). */
@Entity(tableName = "pending_alert")
data class PendingAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: String,
    val lat: Double,
    val lng: Double,
    val transcript: String,
    val isTest: Boolean,
)
