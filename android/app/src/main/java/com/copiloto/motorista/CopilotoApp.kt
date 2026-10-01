package com.copiloto.motorista

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.copiloto.motorista.di.CopilotoContainer
import com.copiloto.motorista.service.DashcamService
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.sync.SyncScheduler

class CopilotoApp : Application(), Configuration.Provider {

    lateinit var container: CopilotoContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = CopilotoContainer(this)
        createOverlayNotificationChannel()
        createDashcamNotificationChannel()
        SyncScheduler.schedulePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private fun createOverlayNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            OverlayService.CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createDashcamNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            DashcamService.CHANNEL_ID,
            getString(R.string.dashcam_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.dashcam_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
