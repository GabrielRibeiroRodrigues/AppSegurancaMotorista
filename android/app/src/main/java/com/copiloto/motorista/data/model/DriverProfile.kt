package com.copiloto.motorista.data.model

/**
 * Driver-configurable costs and acceptance thresholds. Persisted locally in
 * DataStore and mirrored to the backend `DriverProfile` model (Module F).
 *
 * @param fuelPricePerLiter price paid per liter of fuel (R$).
 * @param kmPerLiter average vehicle consumption (km per liter).
 * @param maintenanceCostPerKm wear/maintenance cost attributed to each km (R$).
 * @param targetPerKm R$/km at or above which a ride is considered GREEN.
 * @param minimumPerKm R$/km below which a ride is RED; between this and target it is YELLOW.
 * @param targetPerHour minimum acceptable R$/hour; a ride that would be GREEN by R$/km but
 *   earns less than this per hour is downgraded to YELLOW (never recommended as GREEN).
 * @param dailyGoal the driver's daily net-earnings target (R$), tracked on the history screen.
 * @param voiceEnabled whether the Text-To-Speech announcement is spoken (Module D).
 */
data class DriverProfile(
    val fuelPricePerLiter: Double = 6.00,
    val kmPerLiter: Double = 12.0,
    val maintenanceCostPerKm: Double = 0.25,
    val targetPerKm: Double = 1.80,
    val minimumPerKm: Double = 1.20,
    val targetPerHour: Double = 30.0,
    val dailyGoal: Double = 300.0,
    val voiceEnabled: Boolean = true,
) {
    /** Fuel cost per km derived from price and consumption. */
    val fuelCostPerKm: Double
        get() = if (kmPerLiter > 0.0) fuelPricePerLiter / kmPerLiter else 0.0

    /** Total variable cost attributed to each km driven. */
    val costPerKm: Double
        get() = fuelCostPerKm + maintenanceCostPerKm
}
