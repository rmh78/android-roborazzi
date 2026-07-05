package com.example.roborazzidemo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkScheme = darkColorScheme(
    primary = NexusNeonCyan,
    onPrimary = NexusVoid,
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = NexusNeonCyan,
    secondary = NexusNeonMagenta,
    onSecondary = NexusVoid,
    secondaryContainer = Color(0xFF4A1030),
    onSecondaryContainer = NexusNeonMagenta,
    tertiary = NexusNeonViolet,
    background = NexusVoid,
    onBackground = Color(0xFFE8EEF7),
    surface = NexusDeep,
    onSurface = Color(0xFFE8EEF7),
    onSurfaceVariant = NexusTextDim,
    surfaceVariant = NexusPanel,
    outline = NexusGrid,
    outlineVariant = Color(0xFF243B55),
    error = Color(0xFFFF6B6B),
)

private val NexusLightScheme = lightColorScheme(
    primary = NexusLabCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8ECFF),
    onPrimaryContainer = Color(0xFF003545),
    secondary = NexusLabMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD6E8),
    onSecondaryContainer = Color(0xFF5C0030),
    tertiary = NexusLabViolet,
    background = NexusLabBase,
    onBackground = Color(0xFF0D1B2A),
    surface = NexusLabSurface,
    onSurface = Color(0xFF0D1B2A),
    onSurfaceVariant = NexusLabTextDim,
    surfaceVariant = NexusLabPanel,
    outline = NexusLabGrid,
    outlineVariant = Color(0xFFD0E4F2),
    error = Color(0xFFD32F2F),
)

@Composable
fun RoborazziDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NexusDarkScheme else NexusLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}