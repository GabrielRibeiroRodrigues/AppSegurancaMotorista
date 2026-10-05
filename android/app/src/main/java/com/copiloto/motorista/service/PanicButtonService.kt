package com.copiloto.motorista.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.R
import com.copiloto.motorista.bluetooth.BleConnectionState
import com.copiloto.motorista.data.repository.AlertOrigin
import com.copiloto.motorista.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Keeps the ESP32 panic button connected over BLE while running as a foreground
 * service (type connectedDevice), so a physical press is recognized even with the
 * app closed or the screen off. On a press it fires a real alert
 * ([AlertOrigin.BOTAO_PANICO]), starts the live video and gives haptic/audible
 * feedback. A 30s cooldown plus the BLE sequence counter prevent duplicate alerts.
 */
class PanicButtonService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as CopilotoApp).container
    private val manager get() = container.panicButtonManager

    private var lastFireAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForegroundSafely(BleConnectionState.CONNECTING)

        val config = runCatching { runBlocking { container.panicButtonStore.current() } }.getOrNull()
        if (config == null || !config.enabled || !config.isPaired || !manager.hasConnectPermission()) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (!started) {
            started = true
            isRunning = true
            observe()
            manager.connect(config.deviceAddress!!)
        }
        return START_STICKY
    }

    private fun observe() {
        // Fire on each de-duplicated press.
        scope.launch {
            manager.panicEvents.collectLatest { onButtonPressed() }
        }
        // Reflect the connection status in the notification.
        scope.launch {
            manager.connectionState.collectLatest { updateNotification(it) }
        }
    }

    private fun onButtonPressed() {
        // While the pairing screen is testing, a press only confirms reception —
        // it must not raise a real emergency alert.
        if (testMode) {
            feedback()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastFireAt < FIRE_COOLDOWN_MS) return
        lastFireAt = now
        feedback()
        scope.launch {
            val alertId = runCatching {
                container.panicRepository.fireAlert(
                    transcript = "Botão de pânico acionado",
                    isTest = false,
                    origin = AlertOrigin.BOTAO_PANICO,
                )
            }.getOrNull()
            if (alertId != null) StreamingService.start(this@PanicButtonService, alertId)
        }
    }

    /** Haptic + audible confirmation so the driver knows it fired without looking. */
    private fun feedback() {
        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
            }
        }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            }
        }
    }

    private fun stopEverything() {
        started = false
        isRunning = false
        runCatching { manager.disconnect() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundSafely(state: BleConnectionState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: BleConnectionState) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: BleConnectionState): Notification {
        val text = when (state) {
            BleConnectionState.CONNECTED -> getString(R.string.panic_button_status_connected)
            BleConnectionState.CONNECTING -> getString(R.string.panic_button_status_connecting)
            BleConnectionState.BLUETOOTH_OFF -> getString(R.string.panic_button_status_bt_off)
            BleConnectionState.NO_PERMISSION -> getString(R.string.panic_button_status_no_perm)
            BleConnectionState.UNSUPPORTED -> getString(R.string.panic_button_status_unsupported)
            BleConnectionState.DISCONNECTED -> getString(R.string.panic_button_status_disconnected)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.panic_button_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        started = false
        isRunning = false
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "copiloto_panic_button"
        private const val NOTIFICATION_ID = 93
        private const val FIRE_COOLDOWN_MS = 30_000L

        const val ACTION_START = "com.copiloto.motorista.PANIC_BUTTON_START"
        const val ACTION_STOP = "com.copiloto.motorista.PANIC_BUTTON_STOP"

        @Volatile
        private var started = false

        @Volatile
        var isRunning: Boolean = false
            private set

        /** When true, physical presses only confirm reception (no real alert). */
        @Volatile
        var testMode: Boolean = false

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, PanicButtonService::class.java).apply { action = ACTION_START },
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PanicButtonService::class.java).apply { action = ACTION_STOP },
                )
            }
        }
    }
}
