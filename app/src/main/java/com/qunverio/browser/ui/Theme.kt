package com.qunverio.browser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1B1B6B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF12141F),
    surface = Color(0xFFEEF0FA),
    onSurface = Color(0xFF12141F),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF4A4F6B),
    outline = Color(0xFF8E94B8),
    outlineVariant = Color(0xFFD0D4E8),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BA3FF),
    onPrimary = Color(0xFF14176B),
    primaryContainer = Color(0xFF2D3280),
    onPrimaryContainer = Color(0xFFE0E3FF),
    background = Color(0xFF0B0D1C),
    onBackground = Color(0xFFE8EAFF),
    surface = Color(0xFF14172E),
    onSurface = Color(0xFFE8EAFF),
    surfaceVariant = Color(0xFF232850),
    onSurfaceVariant = Color(0xFFB4B9DD),
    outline = Color(0xFF6A72A8),
    outlineVariant = Color(0xFF343A66),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun QunverioTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
