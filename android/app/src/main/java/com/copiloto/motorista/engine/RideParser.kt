package com.copiloto.motorista.engine

import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource

/**
 * Turns the raw text harvested from a rideshare app's view tree (Module A) into a
 * structured [RideOffer]. Rideshare apps render the pickup leg and the trip leg
 * separately (e.g. "3 min (1,2 km) de distância" + "18 min (7,5 km) de viagem"),
 * so by default every km/minute figure found is summed into a trip total.
 *
 * All parsing is tolerant: a screen that does not contain a price, a distance and
 * a time yields `null` so the caller can ignore non-offer screens.
 */
object RideParser {

    // "R$ 15,50", "R$15", "R$ 1.234,56"
    private val priceRegex = Regex("""R\$\s*([\d.]*\d(?:,\d{2})?)""", RegexOption.IGNORE_CASE)

    // "5 km", "5,3 km", "5.3 Km", "750 m"
    private val kmRegex = Regex("""([\d]+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
    private val meterRegex = Regex("""([\d]+(?:[.,]\d+)?)\s*m(?![a-z])""", RegexOption.IGNORE_CASE)

    // "15 min", "15min", "1 h", "1h20"
    private val hourRegex = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>, source: RideSource): RideOffer? {
        val blob = texts.joinToString(separator = " ") { it.trim() }
            .replace('\n', ' ')
            .trim()
        if (blob.isEmpty()) return null
        return parse(blob, source)
    }

    fun parse(text: String, source: RideSource): RideOffer? {
        val price = parsePrice(text) ?: return null
        val distanceKm = parseDistanceKm(text)
        val minutes = parseMinutes(text)
        if (distanceKm <= 0.0 || minutes <= 0) return null

        return RideOffer(
            source = source,
            grossPrice = price,
            distanceKm = distanceKm,
            timeMinutes = minutes,
            rawText = text.take(500),
        )
    }

    /** Picks the largest currency value on screen (the fare, not a surge badge). */
    fun parsePrice(text: String): Double? =
        priceRegex.findAll(text)
            .mapNotNull { toDouble(it.groupValues[1]) }
            .maxOrNull()

    /** Sums every kilometre figure, plus any metre figure converted to km. */
    fun parseDistanceKm(text: String): Double {
        val km = kmRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.sum()
        val metersAsKm = meterRegex.findAll(text)
            .mapNotNull { toDouble(it.groupValues[1]) }
            .sumOf { it / 1000.0 }
        return km + metersAsKm
    }

    /** Sums every "min" figure and adds any "h" figures as 60 minutes each. */
    fun parseMinutes(text: String): Int {
        val minutes = minuteRegex.findAll(text).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val hours = hourRegex.findAll(text).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        return minutes + hours * 60
    }

    /** Converts "1.234,56" -> 1234.56 and "15" -> 15.0, tolerating either locale. */
    private fun toDouble(raw: String): Double? {
        if (raw.isBlank()) return null
        val normalized = when {
            raw.contains(',') -> raw.replace(".", "").replace(',', '.')
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }
}
