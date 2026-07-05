package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun ListEdgeFadeOverlay(
    modifier: Modifier = Modifier,
    fadeHeight: Dp = VoiceOverlayMetrics.ListEdgeFadeHeight,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(background, Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, background),
                    ),
                ),
        )
    }
}