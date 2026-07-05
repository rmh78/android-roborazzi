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
    primaryContainer = Color(0xFF003D10),
    onPrimaryContainer = Color(0xFF66FF80),
    secondary = NexusNeonMagenta,
    onSecondary = NexusVoid,
    secondaryContainer = Color(0xFF002A0C),
    onSecondaryContainer = Color(0xFF33FF66),
    tertiary = NexusNeonViolet,
    background = NexusVoid,
    onBackground = Color(0xFFC8FFCC),
    surface = NexusDeep,
    onSurface = Color(0xFFC8FFCC),
    onSurfaceVariant = NexusTextDim,
    surfaceVariant = NexusPanel,
    outline = NexusGrid,
    outlineVariant = Color(0xFF1A3D1A),
    error = Color(0xFFFF4444),
)

private val NexusLightScheme = lightColorScheme(
    primary = NexusLabCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E8C0),
    onPrimaryContainer = Color(0xFF003D10),
    secondary = NexusLabMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F0D0),
    onSecondaryContainer = Color(0xFF002A0C),
    tertiary = NexusLabViolet,
    background = NexusLabBase,
    onBackground = Color(0xFF0A1F0A),
    surface = NexusLabSurface,
    onSurface = Color(0xFF0A1F0A),
    onSurfaceVariant = NexusLabTextDim,
    surfaceVariant = NexusLabPanel,
    outline = NexusLabGrid,
    outlineVariant = Color(0xFFD0E8D0),
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