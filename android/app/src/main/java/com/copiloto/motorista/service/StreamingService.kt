package com.copiloto.motorista.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.copiloto.motorista.BuildConfig
import com.copiloto.motorista.R
import com.copiloto.motorista.service.webrtc.WebRtcBroadcaster
import com.copiloto.motorista.ui.MainActivity

/**
 * Streams the driver's camera + microphone to the Central de Operações while an
 * alert is active (DesafioMaker — live video). Foreground service so the OS keeps
 * the WebRTC connection alive; auto-stops after [MAX_DURATION_MS] as a safety cap.
 */
class StreamingService : Service() {

    private var broadcaster: WebRtcBroadcaster? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopEverything() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val alertId = intent?.getStringExtra(EXTRA_ALERT_ID)
        if (alertId.isNullOrEmpty() || !hasPermissions()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        if (broadcaster == null) {
            broadcaster = WebRtcBroadcaster(applicationContext, BuildConfig.SIGNALING_URL, alertId).also {
                runCatching { it.start() }
            }
            isRunning = true
            handler.postDelayed(autoStop, MAX_DURATION_MS)
        }
        return START_REDELIVER_INTENT
    }

    private fun stopEverything() {
        handler.removeCallbacks(autoStop)
        runCatching { broadcaster?.stop() }
        broadcaster = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasPermissions(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return cam == PackageManager.PERMISSION_GRANTED && mic == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
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
        return NotificationCompat.Builder(this, PanicService.CHANNEL_ID)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.streaming_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoStop)
        runCatching { broadcaster?.stop() }
        broadcaster = null
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 93
        private const val MAX_DURATION_MS = 10 * 60 * 1000L
        private const val EXTRA_ALERT_ID = "alert_id"
        const val ACTION_START = "com.copiloto.motorista.STREAM_START"
        const val ACTION_STOP = "com.copiloto.motorista.STREAM_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, alertId: Long) {
            runCatching {
                context.startForegroundService(
                    Intent(context, StreamingService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_ALERT_ID, alertId.toString())
                    },
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, StreamingService::class.java).apply { action = ACTION_STOP },
                )
            }
        }
    }
}
