package com.copiloto.motorista.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.R
import com.copiloto.motorista.data.model.DriverProfile
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
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { userDismissed() }
    private lateinit var overlayController: OverlayController
    private var ttsSpeaker: TtsSpeaker? = null

    // Last presented ride, so the ACEITAR button can mark it accepted (Funcionalidade 2).
    private var lastRecordedId: Long = -1
    private var lastEvaluation: RideEvaluation? = null
    private var lastProfile: DriverProfile? = null

    private val container get() = (application as CopilotoApp).container

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this).apply {
            onDismiss = { userDismissed() }
            onAccept = { handleAccept() }
        }
        ttsSpeaker = TtsSpeaker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        when (intent?.action) {
            ACTION_SHOW_OFFER -> {
                val offer = intent.toOffer()
                if (offer != null) handleOffer(offer) else stopEverything()
            }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun handleOffer(offer: RideOffer) {
        scope.launch {
            val profile = container.driverProfileRepository.current()
            val blacklist = container.riskZoneRepository.current()
            val evaluation = RideCalculator.evaluate(offer, profile, blacklist)
            lastProfile = profile
            // Persist first so the record id is ready before the ACEITAR button can be tapped.
            persist(evaluation)
            present(evaluation, speak = profile.voiceEnabled)
        }
    }

    private fun present(evaluation: RideEvaluation, speak: Boolean) {
        overlayController.show(evaluation)
        if (speak) ttsSpeaker?.announce(evaluation)
        // Auto-hide after a few seconds unless replaced by a newer offer or closed.
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
    }

    private suspend fun persist(evaluation: RideEvaluation) {
        lastEvaluation = evaluation
        lastRecordedId = withContext(Dispatchers.IO) {
            container.rideHistoryRepository.record(evaluation)
        }
        // Note: the ride is NOT synced here. It is synced once the card is resolved
        // (accepted/closed/auto-hidden) so the backend receives the final `accepted`
        // value; the periodic SyncWorker backs up anything left unsynced.
    }

    private fun requestSync() {
        SyncScheduler.syncNow(this)
    }

    /** Close button or auto-hide: hide the card and back up the ride (accepted = false). */
    private fun userDismissed() {
        dismissOverlay()
        requestSync()
    }

    /**
     * ACEITAR tapped: marks the ride accepted and, if this accept makes today's
     * accepted earnings cross the daily goal, speaks the celebratory message.
     */
    private fun handleAccept() {
        val id = lastRecordedId
        val evaluation = lastEvaluation
        val profile = lastProfile
        if (id > 0 && evaluation != null && profile != null) {
            scope.launch {
                val repo = container.rideHistoryRepository
                val before = withContext(Dispatchers.IO) { repo.todayAcceptedProfit() }
                withContext(Dispatchers.IO) { repo.setAccepted(id, true) }
                // Sync after the accepted flag is committed so the backup reflects it.
                requestSync()
                val crossed = com.copiloto.motorista.engine.DailyGoal
                    .crossed(before, evaluation.netProfit, profile.dailyGoal)
                if (crossed && profile.voiceEnabled) {
                    ttsSpeaker?.announceGoalReached()
                }
            }
        }
        // A simulated ride opens the in-trip demo screen (map + panic demonstration).
        if (evaluation?.offer?.isDemo == true) openInTripDemo(evaluation)
        dismissOverlay()
    }

    /** Hands the accepted demo ride to the app and brings the in-trip screen forward. */
    private fun openInTripDemo(evaluation: RideEvaluation) {
        val offer = evaluation.offer
        container.demoTripHolder.value = com.copiloto.motorista.data.model.DemoTrip(
            sourceLabel = offer.displayLabel,
            grossPrice = offer.grossPrice,
            distanceKm = offer.distanceKm,
            timeMinutes = offer.timeMinutes,
            pickup = com.copiloto.motorista.ui.demo.MuzambinhoRoute.ORIGIN_LABEL,
            dropoff = offer.dropoff?.takeIf { it.isNotBlank() }
                ?: com.copiloto.motorista.ui.demo.MuzambinhoRoute.DESTINATION_LABEL,
        )
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra(MainActivity.EXTRA_OPEN_IN_TRIP, true)
                },
            )
        }
    }

    private fun dismissOverlay() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
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
            sourceLabel = getStringExtra(EXTRA_SOURCE_LABEL),
            isDemo = getBooleanExtra(EXTRA_DEMO, false),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        autoHideHandler.removeCallbacks(autoHideRunnable)
        overlayController.hide()
        ttsSpeaker?.shutdown()
        ttsSpeaker = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "copiloto_overlay"
        private const val NOTIFICATION_ID = 42
        private const val AUTO_HIDE_MS = 15_000L

        const val ACTION_SHOW_OFFER = "com.copiloto.motorista.SHOW_OFFER"
        const val ACTION_STOP = "com.copiloto.motorista.STOP_OVERLAY"

        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SOURCE_LABEL = "source_label"
        private const val EXTRA_DEMO = "demo"
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
                putExtra(EXTRA_SOURCE_LABEL, offer.sourceLabel)
                putExtra(EXTRA_DEMO, offer.isDemo)
                putExtra(EXTRA_PRICE, offer.grossPrice)
                putExtra(EXTRA_DISTANCE, offer.distanceKm)
                putExtra(EXTRA_MINUTES, offer.timeMinutes)
                putExtra(EXTRA_PICKUP, offer.pickup)
                putExtra(EXTRA_DROPOFF, offer.dropoff)
                putExtra(EXTRA_RAW, offer.rawText)
            }

        fun showOffer(context: Context, offer: RideOffer) {
            // Starting a foreground service from the background (e.g. from the
            // AccessibilityService) is allowed because the app holds SYSTEM_ALERT_WINDOW,
            // but guard against ForegroundServiceStartNotAllowedException if overlay
            // permission is missing so a real ride never crashes the app.
            runCatching { context.startForegroundService(showOfferIntent(context, offer)) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, OverlayService::class.java).apply { action = ACTION_STOP },
                )
            }
        }
    }
}
