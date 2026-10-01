package com.copiloto.motorista.engine

import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {

    private val profile = DriverProfile(
        fuelPricePerLiter = 6.0,
        kmPerLiter = 12.0,
        maintenanceCostPerKm = 0.25,
        targetPerKm = 1.80,
        minimumPerKm = 1.20,
    )

    private fun offer(price: Double, km: Double, min: Int) =
        RideOffer(RideSource.SIMULATOR, price, km, min)

    @Test
    fun `cost per km combines fuel and maintenance`() {
        // 6.0 / 12.0 = 0.50 fuel + 0.25 maintenance = 0.75
        assertEquals(0.75, profile.costPerKm, 1e-9)
    }

    @Test
    fun `evaluation computes derived metrics`() {
        val result = RideCalculator.evaluate(offer(18.0, 6.0, 12), profile)
        assertEquals(0.75, result.costPerKm, 1e-9)
        assertEquals(4.5, result.totalCost, 1e-9)       // 6 km * 0.75
        assertEquals(3.0, result.grossPerKm, 1e-9)      // 18 / 6
        assertEquals(90.0, result.grossPerHour, 1e-9)   // (18 / 12) * 60
        assertEquals(13.5, result.netProfit, 1e-9)      // 18 - 4.5
    }

    @Test
    fun `high per km ride is green`() {
        val result = RideCalculator.evaluate(offer(20.0, 6.0, 12), profile)
        assertEquals(RideClassification.GREEN, result.classification)
    }

    @Test
    fun `mid per km ride is yellow`() {
        // 9 / 6 = 1.5 R$/km -> between minimum (1.2) and target (1.8)
        val result = RideCalculator.evaluate(offer(9.0, 6.0, 12), profile)
        assertEquals(RideClassification.YELLOW, result.classification)
    }

    @Test
    fun `low per km ride is red`() {
        // 6 / 6 = 1.0 R$/km -> below minimum
        val result = RideCalculator.evaluate(offer(6.0, 6.0, 12), profile)
        assertEquals(RideClassification.RED, result.classification)
    }

    @Test
    fun `ride that loses money is always red`() {
        val expensive = profile.copy(maintenanceCostPerKm = 5.0)
        val result = RideCalculator.evaluate(offer(30.0, 6.0, 12), expensive)
        assertTrue(result.netProfit < 0)
        assertEquals(RideClassification.RED, result.classification)
    }
}
