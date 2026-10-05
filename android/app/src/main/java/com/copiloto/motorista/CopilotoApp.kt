package com.copiloto.motorista

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.copiloto.motorista.di.CopilotoContainer
import java.io.File
import com.copiloto.motorista.service.DashcamService
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.service.PanicButtonService
import com.copiloto.motorista.service.PanicService
import com.copiloto.motorista.sync.SyncScheduler

class CopilotoApp : Application(), Configuration.Provider {

    lateinit var container: CopilotoContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = CopilotoContainer(this)
        initOsmdroid()
        createOverlayNotificationChannel()
        createDashcamNotificationChannel()
        createProtectionNotificationChannel()
        createPanicButtonNotificationChannel()
        SyncScheduler.schedulePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    /** osmdroid (in-trip demo map): identify ourselves to the tile server and keep
     *  the tile cache inside app storage so no storage permission is needed. */
    private fun initOsmdroid() {
        val config = org.osmdroid.config.Configuration.getInstance()
        config.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }

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

    private fun createProtectionNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PanicService.CHANNEL_ID,
            getString(R.string.protection_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.protection_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createPanicButtonNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PanicButtonService.CHANNEL_ID,
            getString(R.string.panic_button_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.panic_button_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
