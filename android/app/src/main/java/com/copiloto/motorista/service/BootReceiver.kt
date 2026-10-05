package com.copiloto.motorista.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.copiloto.motorista.CopilotoApp
import kotlinx.coroutines.runBlocking

/**
 * Re-arms the ESP32 panic button after a reboot (or an app update), so the driver
 * doesn't have to reopen the app for the physical button to work again. Starting a
 * foreground service from BOOT_COMPLETED is a permitted exemption on modern Android.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as CopilotoApp).container
        val config = runCatching { runBlocking { container.panicButtonStore.current() } }.getOrNull()
        if (config != null && config.enabled && config.isPaired) {
            PanicButtonService.start(context)
        }
    }
}
