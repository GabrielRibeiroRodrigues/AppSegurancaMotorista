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
     *
     * Safety override (Funcionalidade 1): if the captured screen text matches any
     * risk-zone [blacklist] keyword, the math is ignored and the ride is flagged
     * [RideClassification.RISK_RED].
     */
    fun evaluate(
        offer: RideOffer,
        profile: DriverProfile,
        blacklist: List<String> = emptyList(),
    ): RideEvaluation {
        val distance = offer.distanceKm.coerceAtLeast(0.0)
        val minutes = offer.timeMinutes.coerceAtLeast(0)

        val costPerKm = profile.costPerKm
        val totalCost = distance * costPerKm
        val grossPerKm = if (distance > 0.0) offer.grossPrice / distance else 0.0
        val grossPerHour = if (minutes > 0) (offer.grossPrice / minutes) * 60.0 else 0.0
        val netProfit = offer.grossPrice - totalCost

        val classification = if (matchesRiskZone(offer, blacklist)) {
            RideClassification.RISK_RED
        } else {
            classify(grossPerKm, grossPerHour, netProfit, profile)
        }

        return RideEvaluation(
            offer = offer,
            costPerKm = costPerKm,
            totalCost = totalCost,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netProfit = netProfit,
            classification = classification,
        )
    }

    /** True when the offer's captured text contains any blacklist keyword (case-insensitive). */
    fun matchesRiskZone(offer: RideOffer, blacklist: List<String>): Boolean {
        if (blacklist.isEmpty()) return false
        val text = listOfNotNull(offer.rawText, offer.pickup, offer.dropoff)
            .joinToString(" ")
            .lowercase()
        if (text.isBlank()) return false
        return blacklist.any { keyword ->
            keyword.isNotBlank() && text.contains(keyword.trim().lowercase())
        }
    }

    /**
     * GREEN at/above target R$/km, RED below the minimum (or when the ride loses
     * money), YELLOW in between. Net profit acts as a hard floor: a ride that
     * does not cover its costs is never shown as acceptable.
     *
     * Additionally, a ride that qualifies as GREEN by R$/km but pays less than the
     * driver's target R$/hour is **downgraded to YELLOW** — a poor hourly rate is
     * never recommended as a great ride.
     */
    private fun classify(
        grossPerKm: Double,
        grossPerHour: Double,
        netProfit: Double,
        profile: DriverProfile,
    ): RideClassification {
        val byPerKm = when {
            netProfit <= 0.0 -> RideClassification.RED
            grossPerKm >= profile.targetPerKm -> RideClassification.GREEN
            grossPerKm >= profile.minimumPerKm -> RideClassification.YELLOW
            else -> RideClassification.RED
        }
        return if (byPerKm == RideClassification.GREEN && grossPerHour < profile.targetPerHour) {
            RideClassification.YELLOW
        } else {
            byPerKm
        }
    }
}
