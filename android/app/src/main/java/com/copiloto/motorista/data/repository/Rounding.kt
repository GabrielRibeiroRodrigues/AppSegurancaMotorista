package com.copiloto.motorista.data.repository

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds to 2 decimal places before sending to the backend. The Django model uses
 * DecimalField(max_digits=10, decimal_places=2), which rejects the ~16-digit
 * doubles the engine produces; rounding here keeps the payload valid.
 */
fun Double.round2(): Double =
    BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()
