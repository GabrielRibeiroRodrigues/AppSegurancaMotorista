package com.copiloto.motorista.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.R
import com.copiloto.motorista.data.model.RideEvaluation
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.engine.RideCalculator
import com.copiloto.motorista.sync.SyncScheduler
import com.copiloto.motorista.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that hosts the floating overlay (Module C) and the voice
 * announcer (Module D). Ride offers arrive here from the AccessibilityService
 * (Module A) or the simulator (Module G); this service runs them through the
 * Calculation Engine, shows the card, speaks the result, and records the ride.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayController: OverlayController
    private var ttsSpeaker: TtsSpeaker? = null

    private val container get() = (application as CopilotoApp).container

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this).apply {
            onDismiss = { dismissOverlay() }
        }
        ttsSpeaker = TtsSpeaker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        when (intent?.action) {
            ACTION_SHOW_OFFER -> intent.toOffer()?.let { handleOffer(it) }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun handleOffer(offer: RideOffer) {
        scope.launch {
            val profile = container.driverProfileRepository.current()
            val evaluation = RideCalculator.evaluate(offer, profile)
            present(evaluation, speak = profile.voiceEnabled)
            persist(evaluation)
        }
    }

    private fun present(evaluation: RideEvaluation, speak: Boolean) {
        overlayController.show(evaluation)
        if (speak) ttsSpeaker?.announce(evaluation)
    }

    private suspend fun persist(evaluation: RideEvaluation) {
        withContext(Dispatchers.IO) {
            container.rideHistoryRepository.record(evaluation)
        }
        SyncScheduler.syncNow(this)
    }

    private fun dismissOverlay() {
        overlayController.hide()
        ttsSpeaker?.stop()
    }

    private fun stopEverything() {
        dismissOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun Intent.toOffer(): RideOffer? {
        val price = getDoubleExtra(EXTRA_PRICE, -1.0)
        val distance = getDoubleExtra(EXTRA_DISTANCE, -1.0)
        val minutes = getIntExtra(EXTRA_MINUTES, -1)
        if (price < 0 || distance < 0 || minutes < 0) return null
        val source = runCatching {
            RideSource.valueOf(getStringExtra(EXTRA_SOURCE) ?: RideSource.UNKNOWN.name)
        }.getOrDefault(RideSource.UNKNOWN)
        return RideOffer(
            source = source,
            grossPrice = price,
            distanceKm = distance,
            timeMinutes = minutes,
            pickup = getStringExtra(EXTRA_PICKUP),
            dropoff = getStringExtra(EXTRA_DROPOFF),
            rawText = getStringExtra(EXTRA_RAW),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController.hide()
        ttsSpeaker?.shutdown()
        ttsSpeaker = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "copiloto_overlay"
        private const val NOTIFICATION_ID = 42

        const val ACTION_SHOW_OFFER = "com.copiloto.motorista.SHOW_OFFER"
        const val ACTION_STOP = "com.copiloto.motorista.STOP_OVERLAY"

        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_PRICE = "price"
        private const val EXTRA_DISTANCE = "distance"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_PICKUP = "pickup"
        private const val EXTRA_DROPOFF = "dropoff"
        private const val EXTRA_RAW = "raw"

        /** Builds the intent that feeds a ride offer to the service. */
        fun showOfferIntent(context: Context, offer: RideOffer): Intent =
            Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_OFFER
                putExtra(EXTRA_SOURCE, offer.source.name)
                putExtra(EXTRA_PRICE, offer.grossPrice)
                putExtra(EXTRA_DISTANCE, offer.distanceKm)
                putExtra(EXTRA_MINUTES, offer.timeMinutes)
                putExtra(EXTRA_PICKUP, offer.pickup)
                putExtra(EXTRA_DROPOFF, offer.dropoff)
                putExtra(EXTRA_RAW, offer.rawText)
            }

        fun showOffer(context: Context, offer: RideOffer) {
            context.startForegroundService(showOfferIntent(context, offer))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
