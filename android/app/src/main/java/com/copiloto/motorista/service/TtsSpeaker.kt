package com.copiloto.motorista.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideEvaluation
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Text-To-Speech announcer (Module D). Speaks the ride classification and the key
 * numbers so the driver can decide without looking at the screen.
 *
 * Example: "Corrida verde. 15 reais, 5 quilômetros. Lucro de 10 reais."
 */
class TtsSpeaker(context: Context) {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = PT_BR
            ready = true
        }
    }

    fun announce(evaluation: RideEvaluation) {
        if (!ready) return
        val phrase = if (evaluation.classification == RideClassification.RISK_RED) {
            RISK_PHRASE
        } else {
            buildPhrase(evaluation)
        }
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun buildPhrase(evaluation: RideEvaluation): String {
        val color = when (evaluation.classification) {
            RideClassification.GREEN -> "verde"
            RideClassification.YELLOW -> "amarela"
            RideClassification.RED -> "vermelha"
            RideClassification.RISK_RED -> "vermelha"
        }
        val price = evaluation.offer.grossPrice.roundToInt()
        val km = formatKm(evaluation.offer.distanceKm)
        val profit = evaluation.netProfit.roundToInt()
        val profitPhrase = if (profit >= 0) {
            "Lucro de $profit reais."
        } else {
            "Prejuízo de ${-profit} reais."
        }
        return "Corrida $color. $price reais, $km. $profitPhrase"
    }

    private fun formatKm(distanceKm: Double): String {
        val rounded = (distanceKm * 10).roundToInt() / 10.0
        val value = if (rounded % 1.0 == 0.0) rounded.toInt().toString()
        else rounded.toString().replace('.', ',')
        return "$value quilômetros"
    }

    private companion object {
        val PT_BR: Locale = Locale("pt", "BR")
        const val UTTERANCE_ID = "ride_eval"
        const val RISK_PHRASE = "Alerta: destino em área de risco"
    }
}
