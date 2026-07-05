package com.example.roborazzidemo.ui.futuristic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.NexusGlowCyan
import com.example.roborazzidemo.theme.NexusGlowMagenta
import com.example.roborazzidemo.theme.NexusLabGrid
import com.example.roborazzidemo.theme.NexusNeonCyan
import com.example.roborazzidemo.theme.NexusNeonMagenta

@Composable
fun FuturisticBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    val gridColor = if (dark) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    } else {
        NexusLabGrid.copy(alpha = 0.55f)
    }
    val accentA = if (dark) NexusGlowCyan else NexusNeonCyan.copy(alpha = 0.10f)
    val accentB = if (dark) NexusGlowMagenta else NexusNeonMagenta.copy(alpha = 0.06f)
    val primary = MaterialTheme.colorScheme.primary
    val horizonAlpha = if (dark) 0.25f else 0.15f
    val nodeAlpha = if (dark) 0.18f else 0.1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accentA, Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                    radius = size.width * 0.7f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(accentB, Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.85f),
                    radius = size.width * 0.55f,
                ),
            )

            val spacing = 48.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += spacing
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += spacing
            }

            // Horizon scan line
            val horizonY = size.height * 0.72f
            drawLine(
                color = primary.copy(alpha = horizonAlpha),
                start = Offset(0f, horizonY),
                end = Offset(size.width, horizonY),
                strokeWidth = 2f,
            )
            drawCircle(
                color = primary.copy(alpha = nodeAlpha),
                radius = 6.dp.toPx(),
                center = Offset(size.width * 0.5f, horizonY),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}