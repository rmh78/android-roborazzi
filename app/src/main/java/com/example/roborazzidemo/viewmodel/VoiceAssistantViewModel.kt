package com.example.roborazzidemo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roborazzidemo.voice.GrokVoiceSession
import com.example.roborazzidemo.voice.VoiceDebugBridge
import com.example.roborazzidemo.voice.VoiceLog
import com.example.roborazzidemo.voice.VoiceSessionListener
import com.example.roborazzidemo.voice.VoiceToolExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TranscriptLine(
    val role: TranscriptRole,
    val text: String,
)

enum class TranscriptRole {
    User,
    Assistant,
}

data class VoiceUiState(
    val isConnected: Boolean = false,
    val status: String = "Disconnected",
    val isAssistantTurnActive: Boolean = false,
    val audioLevel: Float = 0f,
    val liveUserText: String = "",
    val liveAssistantText: String = "",
    val transcriptLines: List<TranscriptLine> = emptyList(),
    val lastToolName: String = "",
    val errorMessage: String? = null,
    val hasApiKey: Boolean = true,
    val hasMicrophonePermission: Boolean = false,
) {
    companion object {
        val RoborazziDisconnected = VoiceUiState(
            isConnected = false,
            status = "Disconnected",
            hasApiKey = true,
            hasMicrophonePermission = true,
        )
    }
}

class VoiceAssistantViewModel(
    private val apiKey: String,
    private val toolExecutor: VoiceToolExecutor,
    private val applicationContext: Context,
) : ViewModel(), VoiceSessionListener {
    private val _uiState = MutableStateFlow(VoiceUiState(hasApiKey = apiKey != "no-api-key"))
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var session: GrokVoiceSession? = null

    fun setMicrophonePermissionGranted(granted: Boolean) {
        VoiceLog.ui("Microphone permission: $granted")
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
    }

    fun connect() {
        if (apiKey == "no-api-key") {
            VoiceLog.w("UI", "Connect blocked — XAI_API_KEY not set at build time")
            _uiState.update {
                it.copy(errorMessage = "Set XAI_API_KEY before building the app.")
            }
            return
        }
        if (session != null) {
            VoiceLog.w("UI", "Connect ignored — session already active")
            return
        }

        VoiceLog.ui("Connect requested")
        val voiceSession = GrokVoiceSession(
            apiKey = apiKey,
            scope = viewModelScope,
            listener = this,
            applicationContext = applicationContext,
        )
        session = voiceSession
        VoiceDebugBridge.sendTextCommand = { text ->
            VoiceLog.i("Debug", "Forwarding text to session: $text")
            voiceSession.sendTextMessage(text)
        }
        VoiceDebugBridge.sendSpokenUserCommand = { text ->
            VoiceLog.i("Debug", "Forwarding spoken user message to session: $text")
            voiceSession.sendSpokenUserMessage(text)
        }
        VoiceDebugBridge.disconnectCommand = { disconnect() }
        VoiceDebugBridge.pulseMicLevelForSpeech = { text ->
            voiceSession.pulseMicLevelForSpeech(text)
        }
        voiceSession.connect()

        viewModelScope.launch {
            voiceSession.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
    }

    fun disconnect() {
        VoiceLog.ui("Disconnect requested")
        clearDebugBridge()
        session?.disconnect()
        session = null
        _uiState.update {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                isAssistantTurnActive = false,
                audioLevel = 0f,
                liveUserText = "",
                liveAssistantText = "",
            )
        }
    }

    override fun onSessionReady() {
        VoiceLog.ui("Session ready")
        _uiState.update {
            it.copy(
                isConnected = true,
                status = "Grok is greeting you…",
                isAssistantTurnActive = true,
                errorMessage = null,
            )
        }
    }

    override fun onStatusChanged(status: String) {
        VoiceLog.ui("Status → $status")
        _uiState.update {
            it.copy(
                status = status,
                isAssistantTurnActive = assistantTurnActiveFromStatus(status),
                errorMessage = if (status.equals("Error", ignoreCase = true)) it.errorMessage else null,
            )
        }
    }

    override fun onUserSpeechStarted() {
        VoiceLog.ui("User speech started")
        _uiState.update { it.copy(liveAssistantText = "") }
    }

    override fun onUserSpeechStopped() {
        VoiceLog.ui("User speech stopped")
    }

    override fun onUserTranscriptUpdated(text: String) {
        VoiceLog.ui("User transcript (live): $text")
        _uiState.update { it.copy(liveUserText = text) }
    }

    override fun onUserTranscriptCompleted(text: String) {
        VoiceLog.ui("User transcript (final): $text")
        _uiState.update {
            it.copy(
                liveUserText = "",
                transcriptLines = it.transcriptLines + TranscriptLine(TranscriptRole.User, text),
            )
        }
    }

    override fun onAssistantTranscriptDelta(delta: String) {
        VoiceLog.d("UI", "Assistant transcript delta: $delta")
        _uiState.update { it.copy(liveAssistantText = it.liveAssistantText + delta) }
    }

    override fun onAssistantTranscriptDone() {
        val assistantText = _uiState.value.liveAssistantText.trim()
        if (assistantText.isNotEmpty()) {
            VoiceLog.ui("Assistant transcript (final): $assistantText")
        }
        _uiState.update { state ->
            val text = state.liveAssistantText.trim()
            if (text.isEmpty()) {
                state
            } else {
                state.copy(
                    liveAssistantText = "",
                    transcriptLines = state.transcriptLines + TranscriptLine(
                        TranscriptRole.Assistant,
                        text,
                    ),
                )
            }
        }
    }

    override fun onFunctionCall(name: String, arguments: JSONObject, callId: String) {
        VoiceLog.ui("Function call: $name (call_id=$callId, args=$arguments)")
        updateActiveTool(name)
        viewModelScope.launch {
            val output = toolExecutor.execute(name, arguments)
            session?.sendToolResult(output, callId)
        }
    }

    override fun onServerToolExecuted(name: String) {
        VoiceLog.ui("Server tool executed: $name")
        updateActiveTool(name)
    }

    private fun updateActiveTool(name: String) {
        _uiState.update {
            it.copy(
                lastToolName = name,
                status = "Running $name…",
                isAssistantTurnActive = true,
            )
        }
    }

    override fun onError(message: String, recoverable: Boolean) {
        VoiceLog.e("UI", "Error (recoverable=$recoverable): $message")
        if (recoverable) {
            _uiState.update { it.copy(errorMessage = message) }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = message,
                    status = "Error",
                    isConnected = false,
                    isAssistantTurnActive = false,
                    audioLevel = 0f,
                )
            }
            clearDebugBridge()
            session = null
        }
    }

    override fun onDisconnected(reason: String?) {
        VoiceLog.ui("Disconnected${reason?.let { ": $it" }.orEmpty()}")
        clearDebugBridge()
        _uiState.update {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                isAssistantTurnActive = false,
                audioLevel = 0f,
                errorMessage = reason,
            )
        }
        session = null
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    private fun clearDebugBridge() {
        VoiceDebugBridge.sendTextCommand = null
        VoiceDebugBridge.sendSpokenUserCommand = null
        VoiceDebugBridge.disconnectCommand = null
        VoiceDebugBridge.pulseMicLevelForSpeech = null
    }

    private fun assistantTurnActiveFromStatus(status: String): Boolean =
        when {
            status.contains("Listening", ignoreCase = true) -> false
            status.contains("Preparing microphone", ignoreCase = true) -> false
            status.contains("You are speaking", ignoreCase = true) -> false
            status.contains("Processing", ignoreCase = true) -> false
            status.contains("Disconnected", ignoreCase = true) -> false
            status.contains("Waiting for conversation", ignoreCase = true) -> false
            status.contains("Configuring session", ignoreCase = true) -> false
            status.equals("Error", ignoreCase = true) -> false
            status.contains("Grok is responding", ignoreCase = true) -> true
            status.contains("Grok is greeting", ignoreCase = true) -> true
            status.contains("Running ", ignoreCase = true) -> true
            status.contains("Running tool", ignoreCase = true) -> true
            else -> false
        }

    class Factory(
        private val apiKey: String,
        private val toolExecutor: VoiceToolExecutor,
        private val applicationContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceAssistantViewModel(apiKey, toolExecutor, applicationContext) as T
        }
    }
}