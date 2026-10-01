package com.copiloto.motorista.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundingTest {

    @Test
    fun `rounds to two decimals half up`() {
        assertEquals(1.77, 1.7653225806451613.round2(), 0.0)
        assertEquals(72.97, 72.96666666666667.round2(), 0.0)
        assertEquals(18.50, 18.5.round2(), 0.0)
        assertEquals(3.0, 2.995.round2(), 0.0)
    }

    @Test
    fun `rounded value has at most two decimal places`() {
        val text = 1.7653225806451613.round2().toBigDecimal().stripTrailingZeros().toPlainString()
        val decimals = text.substringAfter('.', "").length
        assertTrue("decimais=$decimals", decimals <= 2)
    }
}
