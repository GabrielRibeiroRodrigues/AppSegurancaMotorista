package com.copiloto.motorista.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.copiloto.motorista.service.RideAccessibilityService

/** Helpers for the three special permissions the app depends on. */
object PermissionUtils {

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, RideAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun overlaySettingsIntent(context: Context) = android.content.Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun accessibilitySettingsIntent() =
        android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
