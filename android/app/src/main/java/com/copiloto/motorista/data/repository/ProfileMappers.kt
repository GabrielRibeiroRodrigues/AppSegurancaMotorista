package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.remote.dto.DriverProfileDto

fun DriverProfile.toDto() = DriverProfileDto(
    fuelPricePerLiter = fuelPricePerLiter,
    kmPerLiter = kmPerLiter,
    maintenanceCostPerKm = maintenanceCostPerKm,
    targetPerKm = targetPerKm,
    minimumPerKm = minimumPerKm,
    voiceEnabled = voiceEnabled,
)

fun DriverProfileDto.toDomain() = DriverProfile(
    fuelPricePerLiter = fuelPricePerLiter,
    kmPerLiter = kmPerLiter,
    maintenanceCostPerKm = maintenanceCostPerKm,
    targetPerKm = targetPerKm,
    minimumPerKm = minimumPerKm,
    voiceEnabled = voiceEnabled,
)
