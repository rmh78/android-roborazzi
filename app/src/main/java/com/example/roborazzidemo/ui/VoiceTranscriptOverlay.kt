package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.viewmodel.TranscriptLine
import com.example.roborazzidemo.viewmodel.TranscriptRole
import com.example.roborazzidemo.viewmodel.VoiceUiState

@Composable
fun VoiceTranscriptOverlay(
    state: VoiceUiState,
    onConnectChange: (Boolean) -> Unit,
    onSpeakChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("voice_assistant_overlay"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Voice Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = state.isConnected,
                        onCheckedChange = onConnectChange,
                        enabled = state.hasApiKey,
                        modifier = Modifier.testTag("voice_connect_switch"),
                    )
                }
            }

            Text(
                text = disconnectedStatus(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Speak", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = state.isSpeakActive,
                        onCheckedChange = onSpeakChange,
                        modifier = Modifier.testTag("voice_speak_switch"),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mic level", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(8.dp)
                            .width((120 * state.audioLevel).dp.coerceAtLeast(4.dp))
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }

                if (state.lastToolName.isNotBlank()) {
                    Text(
                        text = "Tool: ${state.lastToolName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                val scrollState = rememberScrollState()
                val transcriptText = buildTranscriptText(
                    lines = state.transcriptLines,
                    liveUser = state.liveUserText,
                    liveAssistant = state.liveAssistantText,
                )

                LaunchedEffect(transcriptText) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.transcriptLines.forEach { line ->
                        TranscriptBubble(line)
                    }
                    if (state.liveUserText.isNotBlank()) {
                        LiveTranscriptLine(prefix = "You", text = state.liveUserText)
                    }
                    if (state.liveAssistantText.isNotBlank()) {
                        LiveTranscriptLine(prefix = "Grok", text = state.liveAssistantText)
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(line: TranscriptLine) {
    val prefix = when (line.role) {
        TranscriptRole.User -> "You"
        TranscriptRole.Assistant -> "Grok"
    }
    LiveTranscriptLine(prefix = prefix, text = line.text)
}

@Composable
private fun LiveTranscriptLine(prefix: String, text: String) {
    val prefixColor = when (prefix) {
        "You" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Row {
        Text(
            text = "$prefix: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = prefixColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun disconnectedStatus(state: VoiceUiState): String {
    if (state.isConnected) return state.status
    if (!state.hasApiKey) return "Set XAI_API_KEY before building the app."
    if (!state.hasMicrophonePermission) return "Tap Connect to grant microphone access."
    return state.status
}

private fun buildTranscriptText(
    lines: List<TranscriptLine>,
    liveUser: String,
    liveAssistant: String,
): String {
    return buildString {
        lines.forEach { append(it.text) }
        append(liveUser)
        append(liveAssistant)
    }
}