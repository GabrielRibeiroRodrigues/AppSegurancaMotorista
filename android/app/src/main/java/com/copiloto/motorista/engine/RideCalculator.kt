package com.copiloto.motorista.engine

import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideEvaluation
import com.copiloto.motorista.data.model.RideOffer

/**
 * The Calculation Engine (Module B). Pure, side-effect-free math so it can be
 * unit-tested and shared by the AccessibilityService and the simulator alike.
 */
object RideCalculator {

    /**
     * Evaluates a ride offer against the driver's configured costs and thresholds.
     *
     * Formulas (per spec):
     *  - Cost per km   = (Fuel Price / Km per Liter) + Maintenance Cost per km
     *  - Total Cost    = Distance * Cost per km
     *  - Gross R$/km   = Price / Distance
     *  - Gross R$/hour = (Price / Time in minutes) * 60
     *  - Net Profit    = Price - Total Cost
     */
    fun evaluate(offer: RideOffer, profile: DriverProfile): RideEvaluation {
        val distance = offer.distanceKm.coerceAtLeast(0.0)
        val minutes = offer.timeMinutes.coerceAtLeast(0)

        val costPerKm = profile.costPerKm
        val totalCost = distance * costPerKm
        val grossPerKm = if (distance > 0.0) offer.grossPrice / distance else 0.0
        val grossPerHour = if (minutes > 0) (offer.grossPrice / minutes) * 60.0 else 0.0
        val netProfit = offer.grossPrice - totalCost

        return RideEvaluation(
            offer = offer,
            costPerKm = costPerKm,
            totalCost = totalCost,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netProfit = netProfit,
            classification = classify(grossPerKm, netProfit, profile),
        )
    }

    /**
     * GREEN at/above target R$/km, RED below the minimum (or when the ride loses
     * money), YELLOW in between. Net profit acts as a hard floor: a ride that
     * does not cover its costs is never shown as acceptable.
     */
    private fun classify(
        grossPerKm: Double,
        netProfit: Double,
        profile: DriverProfile,
    ): RideClassification = when {
        netProfit <= 0.0 -> RideClassification.RED
        grossPerKm >= profile.targetPerKm -> RideClassification.GREEN
        grossPerKm >= profile.minimumPerKm -> RideClassification.YELLOW
        else -> RideClassification.RED
    }
}
