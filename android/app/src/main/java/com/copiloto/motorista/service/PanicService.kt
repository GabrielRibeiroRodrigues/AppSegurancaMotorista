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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.R
import com.copiloto.motorista.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.Normalizer
import java.util.Locale

/**
 * Protection mode (DesafioMaker): a foreground service that listens for the
 * driver's configurable trigger phrase with [SpeechRecognizer] and silently fires
 * a panic alert when it hears it. Online recognition (pt-BR), restarted in a loop.
 */
class PanicService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var triggerNormalized = normalize(com.copiloto.motorista.data.settings.PanicStore.DEFAULT_PHRASE)
    private var running = false
    private var lastFireAt = 0L

    private val container get() = (application as CopilotoApp).container

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        if (!hasRecordAudio() || !SpeechRecognizer.isRecognitionAvailable(this)) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        triggerNormalized = normalize(runBlocking { container.panicStore.currentPhrase() })
        startRecognizer()
        isRunning = true
        return START_STICKY
    }

    private fun startRecognizer() {
        if (running) return
        running = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        listen()
    }

    private fun listen() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }
    }

    /** Restart the recognizer after a short delay (it stops on silence/errors). */
    private fun scheduleRestart(recreate: Boolean) {
        if (!running) return
        handler.postDelayed({
            if (!running) return@postDelayed
            if (recreate) {
                runCatching { recognizer?.destroy() }
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(listener)
                }
            } else {
                runCatching { recognizer?.cancel() }
            }
            listen()
        }, RESTART_DELAY_MS)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) = checkFor(partialResults)
        override fun onResults(results: Bundle?) {
            checkFor(results)
            scheduleRestart(recreate = false)
        }

        override fun onError(error: Int) {
            val recreate = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT
            scheduleRestart(recreate)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkFor(bundle: Bundle?) {
        val heard = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val match = heard.any { normalize(it).contains(triggerNormalized) }
        if (match) fire(heard.firstOrNull().orEmpty())
    }

    private fun fire(transcript: String) {
        val now = System.currentTimeMillis()
        if (now - lastFireAt < FIRE_COOLDOWN_MS) return
        lastFireAt = now
        scope.launch {
            val alertId = runCatching {
                container.panicRepository.fireAlert(
                    transcript,
                    isTest = false,
                    origin = com.copiloto.motorista.data.repository.AlertOrigin.VOZ,
                )
            }.getOrNull()
            // On a real alert, stream the camera live to the Central de Operações.
            if (alertId != null) StreamingService.start(this@PanicService, alertId)
        }
    }

    private fun stopEverything() {
        running = false
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
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
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(R.string.protection_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "copiloto_protection"
        private const val NOTIFICATION_ID = 91
        private const val RESTART_DELAY_MS = 350L
        private const val FIRE_COOLDOWN_MS = 30_000L

        const val ACTION_START = "com.copiloto.motorista.PROTECTION_START"
        const val ACTION_STOP = "com.copiloto.motorista.PROTECTION_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, PanicService::class.java).apply { action = ACTION_START },
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PanicService::class.java).apply { action = ACTION_STOP },
                )
            }
        }

        /** Lowercase + strip accents so the phrase match is tolerant. */
        fun normalize(text: String): String {
            val lowered = text.lowercase(Locale("pt", "BR")).trim()
            val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{M}+"), "")
        }
    }
}
