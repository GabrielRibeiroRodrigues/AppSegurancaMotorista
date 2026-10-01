package com.copiloto.motorista.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.copiloto.motorista.R
import com.copiloto.motorista.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Transparent safety dashcam (Module E). Records the trip with CameraX (video +
 * audio) while running as a foreground service with a visible, persistent
 * notification — there is no hidden recording and no camera-preview bypass.
 *
 * Recording is split into fixed-length segments; after each segment finalizes the
 * 5 GB rotating buffer is enforced by deleting the oldest segments
 * ([DashcamStorage]).
 */
class DashcamService : LifecycleService() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false

    private val stopSegmentRunnable = Runnable { activeRecording?.stop() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            else -> {
                if (!hasPermissions()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundSafely()
                startCamera()
            }
        }
        return START_STICKY
    }

    private fun startCamera() {
        if (isRecording) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindAndRecord()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindAndRecord() {
        val provider = cameraProvider ?: return
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
        } catch (e: Exception) {
            stopEverything()
            return
        }

        isRecording = true
        isRunning = true
        startNextSegment()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startNextSegment() {
        if (!isRecording) return
        val capture = videoCapture ?: return
        val file = DashcamStorage.newSegmentFile(this, System.currentTimeMillis())
        val outputOptions = FileOutputOptions.Builder(file).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                onRecordEvent(event)
            }

        handler.postDelayed(stopSegmentRunnable, SEGMENT_DURATION_MS)
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Finalize) {
            handler.removeCallbacks(stopSegmentRunnable)
            enforceBuffer()
            if (isRecording) {
                startNextSegmentIfPermitted()
            }
        }
    }

    private fun startNextSegmentIfPermitted() {
        if (hasPermissions()) startNextSegment() else stopEverything()
    }

    private fun enforceBuffer() {
        ioScope.launch {
            DashcamStorage.enforceBudget(DashcamStorage.recordingsDir(this@DashcamService))
        }
    }

    private fun stopEverything() {
        isRecording = false
        isRunning = false
        handler.removeCallbacks(stopSegmentRunnable)
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED &&
            audio == PackageManager.PERMISSION_GRANTED
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
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DashcamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dashcam_notification_title))
            .setContentText(getString(R.string.dashcam_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.dashcam_stop_action), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        isRecording = false
        isRunning = false
        handler.removeCallbacks(stopSegmentRunnable)
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "copiloto_dashcam"
        private const val NOTIFICATION_ID = 73
        private const val SEGMENT_DURATION_MS = 5 * 60 * 1000L // 5-minute segments

        const val ACTION_START = "com.copiloto.motorista.DASHCAM_START"
        const val ACTION_STOP = "com.copiloto.motorista.DASHCAM_STOP"

        /** Reflects whether a recording session is active (best-effort, for the UI). */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, DashcamService::class.java).apply { action = ACTION_START },
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DashcamService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
