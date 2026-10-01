package com.copiloto.motorista.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.copiloto.motorista.R
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideEvaluation
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Owns the floating ride card drawn with [WindowManager] (Module C). The card is
 * draggable, non-blocking to touches underneath, and reused across offers.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Callback invoked when the user taps the card's close button. */
    var onDismiss: (() -> Unit)? = null

    /** Callback invoked when the user taps the card's ACEITAR button. */
    var onAccept: (() -> Unit)? = null

    val isShowing: Boolean get() = overlayView != null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(evaluation: RideEvaluation) {
        if (overlayView == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_ride_card, null)
            val params = buildLayoutParams()
            makeDraggable(view, params)
            view.findViewById<TextView>(R.id.close_button).setOnClickListener {
                onDismiss?.invoke()
            }
            view.findViewById<TextView>(R.id.accept_button).setOnClickListener {
                onAccept?.invoke()
            }
            windowManager.addView(view, params)
            overlayView = view
            layoutParams = params
        }
        bind(requireNotNull(overlayView), evaluation)
    }

    fun hide() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        layoutParams = null
    }

    private fun bind(view: View, evaluation: RideEvaluation) {
        val color = statusColor(evaluation.classification)

        (view.findViewById<View>(R.id.status_dot).background as? GradientDrawable)
            ?.apply { mutate(); setColor(color) }

        view.findViewById<TextView>(R.id.classification_label).apply {
            text = classificationLabel(evaluation.classification)
            setTextColor(color)
        }
        view.findViewById<TextView>(R.id.source_label).text = evaluation.offer.source.displayName

        view.findViewById<TextView>(R.id.net_profit).apply {
            text = currency.format(evaluation.netProfit)
            setTextColor(color)
        }
        view.findViewById<TextView>(R.id.per_km).text =
            "${currency.format(evaluation.grossPerKm)}/km"
        view.findViewById<TextView>(R.id.per_hour).text =
            "${currency.format(evaluation.grossPerHour)}/h"

        val km = (evaluation.offer.distanceKm * 10).roundToInt() / 10.0
        view.findViewById<TextView>(R.id.trip_summary).text =
            "${currency.format(evaluation.offer.grossPrice)} · ${km}km · ${evaluation.offer.timeMinutes}min"
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // Fix the card width here (not in XML): a view inflated with a null parent
        // and added via WindowManager takes its size from these LayoutParams, so a
        // width in the layout file is ignored and the content would wrap/clip.
        val widthPx = (CARD_WIDTH_DP * context.resources.displayMetrics.density).toInt()
        return WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE keeps keystrokes/touches flowing to the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Treat a tiny movement as a tap so child click listeners still fire.
                    val moved = abs(event.rawX - touchX) > TAP_SLOP ||
                        abs(event.rawY - touchY) > TAP_SLOP
                    if (!moved) view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun statusColor(classification: RideClassification): Int = when (classification) {
        RideClassification.GREEN -> GREEN
        RideClassification.YELLOW -> YELLOW
        RideClassification.RED -> RED
        RideClassification.RISK_RED -> RED
    }

    private fun classificationLabel(classification: RideClassification): String = when (classification) {
        RideClassification.GREEN -> "ACEITAR"
        RideClassification.YELLOW -> "AVALIAR"
        RideClassification.RED -> "RECUSAR"
        RideClassification.RISK_RED -> "⚠ ÁREA DE RISCO"
    }

    private companion object {
        const val CARD_WIDTH_DP = 320
        const val TAP_SLOP = 12f
        const val GREEN = 0xFF22C55E.toInt()
        const val YELLOW = 0xFFF59E0B.toInt()
        const val RED = 0xFFEF4444.toInt()
    }
}
