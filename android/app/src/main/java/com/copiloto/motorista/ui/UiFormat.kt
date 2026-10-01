package com.copiloto.motorista.ui

import androidx.compose.ui.graphics.Color
import com.copiloto.motorista.data.model.RideClassification
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiFormat {
    private val ptBr = Locale("pt", "BR")
    private val currency = NumberFormat.getCurrencyInstance(ptBr)
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", ptBr)

    fun money(value: Double): String = currency.format(value)

    fun dateTime(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

    fun classificationColor(name: String): Color = when (name) {
        RideClassification.GREEN.name -> Color(0xFF22C55E)
        RideClassification.YELLOW.name -> Color(0xFFF59E0B)
        RideClassification.RED.name -> Color(0xFFEF4444)
        RideClassification.RISK_RED.name -> Color(0xFFEF4444)
        else -> Color(0xFF9E9E9E)
    }

    fun classificationLabel(name: String): String = when (name) {
        RideClassification.GREEN.name -> "Aceitar"
        RideClassification.YELLOW.name -> "Avaliar"
        RideClassification.RED.name -> "Recusar"
        RideClassification.RISK_RED.name -> "⚠ Risco"
        else -> "—"
    }
}
