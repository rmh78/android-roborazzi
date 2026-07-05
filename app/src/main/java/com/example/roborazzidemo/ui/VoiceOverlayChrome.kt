package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.NexusVoid
import com.example.roborazzidemo.viewmodel.VoiceUiState

@Composable
fun VoiceOverlayChrome(
    state: VoiceUiState,
    onConnectChange: (Boolean) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NexusVoid.copy(alpha = 0.55f),
                            NexusVoid.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )

        VoiceTranscriptOverlay(
            state = state,
            onConnectChange = onConnectChange,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}