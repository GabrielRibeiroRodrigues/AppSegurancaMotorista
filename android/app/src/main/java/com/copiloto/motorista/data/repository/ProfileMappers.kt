package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.remote.dto.DriverProfileDto

fun DriverProfile.toDto() = DriverProfileDto(
    fuelPricePerLiter = fuelPricePerLiter.round2(),
    kmPerLiter = kmPerLiter.round2(),
    maintenanceCostPerKm = maintenanceCostPerKm.round2(),
    targetPerKm = targetPerKm.round2(),
    minimumPerKm = minimumPerKm.round2(),
    targetPerHour = targetPerHour.round2(),
    dailyGoal = dailyGoal.round2(),
    voiceEnabled = voiceEnabled,
)

fun DriverProfileDto.toDomain() = DriverProfile(
    fuelPricePerLiter = fuelPricePerLiter,
    kmPerLiter = kmPerLiter,
    maintenanceCostPerKm = maintenanceCostPerKm,
    targetPerKm = targetPerKm,
    minimumPerKm = minimumPerKm,
    targetPerHour = targetPerHour,
    dailyGoal = dailyGoal,
    voiceEnabled = voiceEnabled,
)
