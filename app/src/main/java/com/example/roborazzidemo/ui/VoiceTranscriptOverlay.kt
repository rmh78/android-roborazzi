package com.example.roborazzidemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsBlueDeep
import com.example.roborazzidemo.theme.LcarsOrange
import com.example.roborazzidemo.theme.NexusOverlayInset
import com.example.roborazzidemo.theme.NexusOverlayPanel
import com.example.roborazzidemo.theme.NexusOverlayText
import com.example.roborazzidemo.theme.NexusOverlayTextDim
import com.example.roborazzidemo.ui.futuristic.LcarsBarShape
import com.example.roborazzidemo.ui.futuristic.LcarsPanelShape
import com.example.roborazzidemo.viewmodel.TranscriptLine
import com.example.roborazzidemo.viewmodel.TranscriptRole
import com.example.roborazzidemo.viewmodel.VoiceUiState

@Composable
fun VoiceTranscriptOverlay(
    state: VoiceUiState,
    onConnectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("voice_assistant_overlay"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .clip(LcarsBarShape())
                .background(LcarsOrange),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LcarsPanelShape())
                .background(NexusOverlayPanel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "VOICE INTERFACE",
                        style = MaterialTheme.typography.labelMedium,
                        color = LcarsOrange,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Voice Assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusOverlayTextDim,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LINK",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isConnected) {
                            LcarsOrange
                        } else {
                            NexusOverlayTextDim
                        },
                    )
                    Switch(
                        checked = state.isConnected,
                        onCheckedChange = onConnectChange,
                        enabled = state.hasApiKey,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LcarsOrange,
                            checkedTrackColor = LcarsOrange.copy(alpha = 0.35f),
                            uncheckedThumbColor = NexusOverlayTextDim,
                            uncheckedTrackColor = NexusOverlayInset,
                        ),
                        modifier = Modifier
                            .testTag("voice_connect_switch")
                            .semantics { contentDescription = "voice-connect-switch" },
                    )
                }
            }

            Text(
                text = disconnectedStatus(state),
                style = MaterialTheme.typography.labelSmall,
                color = NexusOverlayTextDim,
                modifier = Modifier
                    .testTag("voice_status_text")
                    .semantics {
                        contentDescription = "voice-status-${disconnectedStatus(state)}"
                    },
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        contentDescription = "voice-mic-level"
                    },
                ) {
                    Text(
                        "SIG",
                        style = MaterialTheme.typography.labelSmall,
                        color = LcarsBlue,
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(8.dp)
                            .width((8 + 112 * state.audioLevel).dp)
                            .clip(LcarsBarShape())
                            .background(LcarsOrange)
                            .semantics {
                                stateDescription = "audio-level-${"%.4f".format(state.audioLevel)}"
                            }
                            .testTag("voice_mic_level_bar"),
                    )
                }

                if (state.lastToolName.isNotBlank()) {
                    Text(
                        text = "MODULE // ${state.lastToolName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LcarsBlue,
                        modifier = Modifier.semantics {
                            contentDescription = "voice-last-tool-${state.lastToolName}"
                        },
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

                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .semantics(mergeDescendants = false) {
                            contentDescription = "voice-transcript-summary-${turnSummary(state)}"
                        },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexusOverlayInset)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.transcriptLines.forEachIndexed { index, line ->
                        TranscriptBubble(line = line, turnIndex = index)
                    }
                    val liveTurnBase = state.transcriptLines.size
                    if (state.liveUserText.isNotBlank()) {
                        LiveTranscriptLine(
                            prefix = "You",
                            text = state.liveUserText,
                            turnIndex = liveTurnBase,
                            isLive = true,
                        )
                    }
                    if (state.liveAssistantText.isNotBlank()) {
                        LiveTranscriptLine(
                            prefix = "Grok",
                            text = state.liveAssistantText,
                            turnIndex = liveTurnBase + if (state.liveUserText.isNotBlank()) 1 else 0,
                            isLive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(line: TranscriptLine, turnIndex: Int) {
    val prefix = when (line.role) {
        TranscriptRole.User -> "You"
        TranscriptRole.Assistant -> "Grok"
    }
    LiveTranscriptLine(prefix = prefix, text = line.text, turnIndex = turnIndex)
}

@Composable
private fun LiveTranscriptLine(
    prefix: String,
    text: String,
    turnIndex: Int,
    isLive: Boolean = false,
) {
    val role = prefix.lowercase()
    val prefixColor = when (prefix) {
        "You" -> LcarsOrange
        else -> LcarsBlueDeep
    }
    val prefixLabel = when (prefix) {
        "You" -> "USR"
        else -> "AI"
    }
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (isLive) {
                "voice-transcript-live-$role"
            } else {
                "voice-transcript-$turnIndex-$role"
            }
        },
    ) {
        Text(
            text = "$prefixLabel: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = prefixColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NexusOverlayText,
        )
    }
}

private fun disconnectedStatus(state: VoiceUiState): String {
    if (state.isConnected) return state.status
    if (!state.hasApiKey) return "Set XAI_API_KEY before building the app."
    if (!state.hasMicrophonePermission) return "Tap Connect to grant microphone access."
    return state.status
}

private fun turnSummary(state: VoiceUiState): String = buildString {
    state.transcriptLines.forEach { line ->
        if (isNotEmpty()) append(',')
        append(
            when (line.role) {
                TranscriptRole.User -> "you"
                TranscriptRole.Assistant -> "grok"
            },
        )
    }
    if (state.liveUserText.isNotBlank()) {
        if (isNotEmpty()) append(',')
        append("you")
    }
    if (state.liveAssistantText.isNotBlank()) {
        if (isNotEmpty()) append(',')
        append("grok")
    }
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