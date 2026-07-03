package com.example.roborazzidemo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roborazzidemo.voice.GrokVoiceSession
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
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
    }

    fun connect() {
        if (apiKey == "no-api-key") {
            _uiState.update {
                it.copy(errorMessage = "Set XAI_API_KEY before building the app.")
            }
            return
        }
        if (session != null) return

        val voiceSession = GrokVoiceSession(
            apiKey = apiKey,
            scope = viewModelScope,
            listener = this,
        )
        session = voiceSession
        voiceSession.connect()

        viewModelScope.launch {
            voiceSession.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
    }

    fun disconnect() {
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
        _uiState.update {
            it.copy(isConnected = true, status = "Listening", errorMessage = null)
        }
    }

    override fun onStatusChanged(status: String) {
        _uiState.update { it.copy(status = status) }
    }

    override fun onUserSpeechStarted() {
        _uiState.update { it.copy(liveAssistantText = "") }
    }

    override fun onUserSpeechStopped() = Unit

    override fun onUserTranscriptUpdated(text: String) {
        _uiState.update { it.copy(liveUserText = text) }
    }

    override fun onUserTranscriptCompleted(text: String) {
        _uiState.update {
            it.copy(
                liveUserText = "",
                transcriptLines = it.transcriptLines + TranscriptLine(TranscriptRole.User, text),
            )
        }
    }

    override fun onAssistantTranscriptDelta(delta: String) {
        _uiState.update { it.copy(liveAssistantText = it.liveAssistantText + delta) }
    }

    override fun onAssistantTranscriptDone() {
        _uiState.update { state ->
            val assistantText = state.liveAssistantText.trim()
            if (assistantText.isEmpty()) {
                state
            } else {
                state.copy(
                    liveAssistantText = "",
                    transcriptLines = state.transcriptLines + TranscriptLine(
                        TranscriptRole.Assistant,
                        assistantText,
                    ),
                )
            }
        }
    }

    override fun onFunctionCall(name: String, arguments: JSONObject, callId: String) {
        _uiState.update { it.copy(lastToolName = name, status = "Running $name…") }
        viewModelScope.launch {
            val output = toolExecutor.execute(name, arguments)
            session?.sendToolResult(output, callId)
            _uiState.update { it.copy(status = "Listening") }
        }
    }

    override fun onError(message: String) {
        _uiState.update { it.copy(errorMessage = message, status = "Error") }
    }

    override fun onDisconnected() {
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