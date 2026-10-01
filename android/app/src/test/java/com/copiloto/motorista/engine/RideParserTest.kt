package com.copiloto.motorista.engine

import com.copiloto.motorista.data.model.RideSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideParserTest {

    @Test
    fun `parses brazilian currency with cents`() {
        assertEquals(15.50, RideParser.parsePrice("Ganhe R$ 15,50 nesta viagem")!!, 1e-9)
    }

    @Test
    fun `parses currency with thousand separator`() {
        assertEquals(1234.56, RideParser.parsePrice("R$ 1.234,56")!!, 1e-9)
    }

    @Test
    fun `picks the largest currency value`() {
        assertEquals(22.0, RideParser.parsePrice("Taxa R$ 2,00 total R$ 22")!!, 1e-9)
    }

    @Test
    fun `sums pickup and trip distances`() {
        assertEquals(8.7, RideParser.parseDistanceKm("1,2 km até o cliente, 7,5 km de viagem"), 1e-9)
    }

    @Test
    fun `converts meters to km`() {
        assertEquals(0.75, RideParser.parseDistanceKm("750 m de distância"), 1e-9)
    }

    @Test
    fun `sums minutes and hours`() {
        assertEquals(75, RideParser.parseMinutes("1 h de viagem e 15 min até o cliente"))
    }

    @Test
    fun `parses a complete uber-style offer`() {
        val text = "UBER X  R$ 18,50  3 min (1,2 km) de distância  12 min (6,0 km) de viagem"
        val offer = RideParser.parse(text, RideSource.UBER)
        assertNotNull(offer)
        requireNotNull(offer)
        assertEquals(18.50, offer.grossPrice, 1e-9)
        assertEquals(7.2, offer.distanceKm, 1e-9)   // 1.2 + 6.0
        assertEquals(15, offer.timeMinutes)         // 3 + 12
        assertTrue(offer.isComplete)
    }

    @Test
    fun `returns null when no price is present`() {
        assertNull(RideParser.parse("Procurando corridas...", RideSource.UBER))
    }

    @Test
    fun `returns null when distance or time is missing`() {
        assertNull(RideParser.parse("R$ 18,50 oferta especial", RideSource.UBER))
    }
}
