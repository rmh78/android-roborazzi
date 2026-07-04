package com.example.roborazzidemo.viewmodel

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
    val audioLevel: Float = 0f,
    val liveUserText: String = "",
    val liveAssistantText: String = "",
    val transcriptLines: List<TranscriptLine> = emptyList(),
    val lastToolName: String = "",
    val errorMessage: String? = null,
    val hasApiKey: Boolean = true,
    val hasMicrophonePermission: Boolean = false,
)

class VoiceAssistantViewModel(
    private val apiKey: String,
    private val toolExecutor: VoiceToolExecutor,
) : ViewModel(), VoiceSessionListener {
    private val _uiState = MutableStateFlow(VoiceUiState(hasApiKey = apiKey != "no-api-key"))
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var session: GrokVoiceSession? = null

    init {
        viewModelScope.launch {
            // audio level is wired after connect
        }
    }

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
        )
        session = voiceSession
        VoiceDebugBridge.sendTextCommand = { text ->
            VoiceLog.i("Debug", "Forwarding text to session: $text")
            voiceSession.sendTextMessage(text)
        }
        VoiceDebugBridge.disconnectCommand = { disconnect() }
        voiceSession.connect()

        viewModelScope.launch {
            voiceSession.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
    }

    fun disconnect() {
        VoiceLog.ui("Disconnect requested")
        VoiceDebugBridge.sendTextCommand = null
        VoiceDebugBridge.disconnectCommand = null
        session?.disconnect()
        session = null
        _uiState.update {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                audioLevel = 0f,
                liveUserText = "",
                liveAssistantText = "",
            )
        }
    }

    override fun onSessionReady() {
        VoiceLog.ui("Session ready")
        _uiState.update {
            it.copy(isConnected = true, status = "Listening", errorMessage = null)
        }
    }

    override fun onStatusChanged(status: String) {
        VoiceLog.ui("Status → $status")
        _uiState.update { it.copy(status = status) }
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
        _uiState.update { it.copy(lastToolName = name, status = "Running $name…") }
        viewModelScope.launch {
            val output = toolExecutor.execute(name, arguments)
            session?.sendToolResult(output, callId)
        }
    }

    override fun onError(message: String) {
        VoiceLog.e("UI", "Error: $message")
        _uiState.update { it.copy(errorMessage = message, status = "Error") }
    }

    override fun onDisconnected() {
        VoiceLog.ui("Disconnected")
        VoiceDebugBridge.sendTextCommand = null
        VoiceDebugBridge.disconnectCommand = null
        _uiState.update {
            it.copy(
                isConnected = false,
                status = "Disconnected",
                audioLevel = 0f,
            )
        }
        session = null
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    class Factory(
        private val apiKey: String,
        private val toolExecutor: VoiceToolExecutor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceAssistantViewModel(apiKey, toolExecutor) as T
        }
    }
}