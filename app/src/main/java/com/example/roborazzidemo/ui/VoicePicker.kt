package com.example.roborazzidemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roborazzidemo.theme.LcarsBlue
import com.example.roborazzidemo.theme.LcarsOrange
import com.example.roborazzidemo.theme.NexusOverlayInset
import com.example.roborazzidemo.theme.NexusOverlayText
import com.example.roborazzidemo.theme.NexusOverlayTextDim
import com.example.roborazzidemo.voice.VoiceOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePicker(
    selectedVoiceId: String,
    availableVoices: List<VoiceOption>,
    voicesLoading: Boolean,
    voicesLoadError: String?,
    enabled: Boolean,
    onVoiceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedVoice = availableVoices.firstOrNull { it.id == selectedVoiceId }
    val displayText = when {
        voicesLoading && availableVoices.isEmpty() -> "Loading voices…"
        selectedVoice != null -> selectedVoice.name
        else -> selectedVoiceId.replaceFirstChar { it.uppercase() }
    }
    val menuEnabled = enabled && availableVoices.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "VOICE",
            style = MaterialTheme.typography.labelSmall,
            color = LcarsBlue,
            fontWeight = FontWeight.Bold,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (menuEnabled) expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_picker")
                .semantics {
                    contentDescription = "voice-picker-$selectedVoiceId"
                },
        ) {
            TextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                enabled = menuEnabled,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NexusOverlayInset,
                    unfocusedContainerColor = NexusOverlayInset,
                    disabledContainerColor = NexusOverlayInset,
                    focusedTextColor = NexusOverlayText,
                    unfocusedTextColor = NexusOverlayText,
                    disabledTextColor = NexusOverlayTextDim,
                    focusedIndicatorColor = LcarsOrange,
                    unfocusedIndicatorColor = NexusOverlayTextDim,
                    disabledIndicatorColor = NexusOverlayTextDim,
                    cursorColor = LcarsOrange,
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = NexusOverlayInset,
            ) {
                availableVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = voice.name,
                                    color = NexusOverlayText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = voice.id,
                                    color = NexusOverlayTextDim,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onVoiceSelected(voice.id)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "voice-option-${voice.id}"
                        },
                    )
                }
            }
        }
        voicesLoadError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}