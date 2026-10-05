package com.copiloto.motorista.ui.demo

/**
 * A fixed, illustrative route through Muzambinho-MG for the in-trip demo. These are
 * representative points around the town center (~ -21.376, -46.526), not a routed
 * path — enough for a believable animated trip on the map.
 *
 * Stored as (lat, lng) pairs so this file stays free of the osmdroid dependency.
 */
object MuzambinhoRoute {

    const val ORIGIN_LABEL = "Centro"
    const val DESTINATION_LABEL = "Bairro Alto"

    /** Ordered points the car animates through, origin → destination. */
    val POINTS: List<Pair<Double, Double>> = listOf(
        -21.37310 to -46.52430, // Centro (origem)
        -21.37420 to -46.52560,
        -21.37560 to -46.52660,
        -21.37720 to -46.52710,
        -21.37880 to -46.52680,
        -21.38020 to -46.52560,
        -21.38150 to -46.52430,
        -21.38260 to -46.52300,
        -21.38380 to -46.52170,
        -21.38500 to -46.52050, // Bairro Alto (destino)
    )

    val ORIGIN: Pair<Double, Double> get() = POINTS.first()
    val DESTINATION: Pair<Double, Double> get() = POINTS.last()

    /** Rough geographic center, for the initial map camera. */
    val CENTER: Pair<Double, Double> = -21.37905 to -46.52390
}
