package com.copiloto.motorista.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BrandGreen = Color(0xFF0E7C5A)
private val BrandGreenDark = Color(0xFF34D399)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    secondary = Color(0xFF0F766E),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0B0F12),
    surface = Color(0xFF151A1F),
)

@Composable
fun CopilotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            window.statusBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
