package com.example.mobile3dstudio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColors = darkColorScheme(
    primary = Color(0xFF9CB8FF),
    onPrimary = Color(0xFF10264E),
    secondary = Color(0xFF9FE4D2),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF151923),
    surfaceVariant = Color(0xFF222938),
    onSurface = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFFAAB4C5),
    outline = Color(0xFF3B4658)
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudioColors, content = content)
}
