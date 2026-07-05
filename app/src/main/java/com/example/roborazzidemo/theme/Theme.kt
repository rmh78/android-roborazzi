package com.example.roborazzidemo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkScheme = darkColorScheme(
    primary = NexusNeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF553300),
    onPrimaryContainer = LcarsPeach,
    secondary = NexusNeonMagenta,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A3058),
    onSecondaryContainer = LcarsBlue,
    tertiary = NexusNeonViolet,
    onTertiary = Color.Black,
    background = NexusVoid,
    onBackground = Color.White,
    surface = NexusDeep,
    onSurface = Color.White,
    onSurfaceVariant = NexusTextDim,
    surfaceVariant = NexusPanel,
    outline = NexusGrid,
    outlineVariant = Color(0xFF243850),
    error = LcarsRed,
)

private val NexusLightScheme = lightColorScheme(
    primary = NexusLabCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF553300),
    onPrimaryContainer = LcarsPeach,
    secondary = NexusLabMagenta,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF243858),
    onSecondaryContainer = LcarsBlue,
    tertiary = NexusLabViolet,
    onTertiary = Color.Black,
    background = NexusLabBase,
    onBackground = Color.White,
    surface = NexusLabSurface,
    onSurface = Color.White,
    onSurfaceVariant = NexusLabTextDim,
    surfaceVariant = NexusLabPanel,
    outline = NexusLabGrid,
    outlineVariant = Color(0xFF3A5070),
    error = LcarsRed,
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