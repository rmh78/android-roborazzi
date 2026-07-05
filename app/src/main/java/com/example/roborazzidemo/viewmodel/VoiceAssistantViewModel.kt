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
    val isAssistantPlaybackActive: Boolean = false,
    val isUserTurnAllowed: Boolean = false,
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
    private var assistantBlockStartIndex: Int? = null
    private var userSpeechActive: Boolean = false
    private var pendingUserTranscript: String? = null
    private var skipNextSpeechStoppedCommit: Boolean = false

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
                isAssistantPlaybackActive = false,
                isUserTurnAllowed = false,
                audioLevel = 0f,
                liveUserText = "",
                liveAssistantText = "",
            )
        }
        sessionUserTurnAllowed = false
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
        _uiState.update { state ->
            val responding = status.contains("Grok is responding", ignoreCase = true)
            val updated = if (responding) {
                val flushed = flushLiveUserToLines(state)
                if (assistantBlockStartIndex == null) {
                    assistantBlockStartIndex = flushed.transcriptLines.size
                }
                flushed
            } else {
                state
            }
            applyUiUserTurnGate(
                updated.copy(
                    status = status,
                    isAssistantTurnActive = assistantTurnActiveFromStatus(status),
                    errorMessage = if (status.equals("Error", ignoreCase = true)) updated.errorMessage else null,
                ),
            )
        }
    }

    override fun onVoiceSyncChanged(assistantPlaybackActive: Boolean, userTurnAllowed: Boolean) {
        sessionUserTurnAllowed = userTurnAllowed
        _uiState.update { state ->
            applyUiUserTurnGate(
                state.copy(isAssistantPlaybackActive = assistantPlaybackActive),
            )
        }
    }

    private var sessionUserTurnAllowed: Boolean = false

    private fun applyUiUserTurnGate(state: VoiceUiState): VoiceUiState {
        val allowed = state.isConnected &&
            sessionUserTurnAllowed &&
            !state.isAssistantPlaybackActive &&
            state.liveAssistantText.isBlank() &&
            !userSpeechActive &&
            userTurnAllowedFromStatus(state.status)
        return state.copy(isUserTurnAllowed = allowed)
    }

    private fun userTurnAllowedFromStatus(status: String): Boolean =
        when {
            status.contains("Listening — ask a question", ignoreCase = true) -> true
            status.equals("Listening", ignoreCase = true) -> true
            else -> false
        }

    override fun onUserSpeechStarted() {
        VoiceLog.ui("User speech started")
        userSpeechActive = true
        pendingUserTranscript = null
        assistantBlockStartIndex = null
        _uiState.update { applyUiUserTurnGate(it.copy(liveAssistantText = "", liveUserText = "")) }
    }

    override fun onUserSpeechStopped() {
        VoiceLog.ui("User speech stopped")
        userSpeechActive = false
        if (skipNextSpeechStoppedCommit) {
            skipNextSpeechStoppedCommit = false
            pendingUserTranscript = null
            _uiState.update { it.copy(liveUserText = "") }
            return
        }
        _uiState.update { state ->
            val text = longestTranscript(pendingUserTranscript, state.liveUserText).trim()
            if (text.isBlank()) return@update state
            pendingUserTranscript = null
            assistantBlockStartIndex = null
            state.copy(
                liveUserText = "",
                transcriptLines = insertOrMergeUserLine(
                    lines = state.transcriptLines,
                    text = text,
                ),
            )
        }
    }

    override fun onUserTranscriptUpdated(text: String) {
        VoiceLog.ui("User transcript (live): $text")
        pendingUserTranscript = longestTranscript(pendingUserTranscript, text)
        _uiState.update { state ->
            if (state.isAssistantTurnActive && state.liveAssistantText.isNotBlank()) {
                state
            } else {
                state.copy(liveUserText = text)
            }
        }
    }

    override fun onUserTranscriptCompleted(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        VoiceLog.ui("User transcript (final): $trimmed")
        pendingUserTranscript = longestTranscript(pendingUserTranscript, trimmed)
        if (userSpeechActive) {
            _uiState.update { state ->
                state.copy(liveUserText = longestTranscript(state.liveUserText, trimmed))
            }
            return
        }
        commitUserTranscript(trimmed)
    }

    override fun onUserTranscriptInjected(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        VoiceLog.ui("User transcript (injected): $trimmed")
        userSpeechActive = false
        pendingUserTranscript = null
        skipNextSpeechStoppedCommit = true
        commitUserTranscript(trimmed, appendOnly = true)
    }

    override fun onAssistantTranscriptDelta(delta: String) {
        VoiceLog.d("UI", "Assistant transcript delta: $delta")
        _uiState.update { state ->
            val flushed = flushLiveUserToLines(state)
            if (assistantBlockStartIndex == null) {
                assistantBlockStartIndex = flushed.transcriptLines.size
            }
            applyUiUserTurnGate(flushed.copy(liveAssistantText = flushed.liveAssistantText + delta))
        }
    }

    override fun onAssistantTranscriptDone() {
        val assistantText = _uiState.value.liveAssistantText.trim()
        if (assistantText.isNotEmpty()) {
            VoiceLog.ui("Assistant transcript (final): $assistantText")
        }
        _uiState.update { state ->
            val flushed = flushLiveUserToLines(state)
            val text = flushed.liveAssistantText.trim()
            val updated = if (text.isEmpty()) {
                flushed
            } else {
                flushed.copy(
                    liveAssistantText = "",
                    transcriptLines = flushed.transcriptLines + TranscriptLine(
                        TranscriptRole.Assistant,
                        text,
                    ),
                )
            }
            applyUiUserTurnGate(updated)
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
                isAssistantPlaybackActive = false,
                isUserTurnAllowed = false,
                audioLevel = 0f,
                errorMessage = reason,
            )
        }
        sessionUserTurnAllowed = false
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

    private fun commitUserTranscript(text: String, appendOnly: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            state.copy(
                liveUserText = "",
                transcriptLines = insertOrMergeUserLine(
                    lines = state.transcriptLines,
                    text = trimmed,
                    insertBeforeIndex = if (appendOnly) {
                        null
                    } else {
                        assistantBlockStartIndex
                            ?: lateUserInsertIndex(state.transcriptLines)
                    },
                ),
            )
        }
        assistantBlockStartIndex = null
    }

    private fun longestTranscript(existing: String?, incoming: String): String {
        val trimmed = incoming.trim()
        if (existing.isNullOrBlank()) return trimmed
        return when {
            trimmed.length >= existing.length && trimmed.startsWith(existing) -> trimmed
            existing.length > trimmed.length && existing.startsWith(trimmed) -> existing
            trimmed.length >= existing.length -> trimmed
            else -> existing
        }
    }

    private fun flushLiveUserToLines(state: VoiceUiState): VoiceUiState {
        val live = state.liveUserText.trim()
        if (live.isBlank()) return state
        return state.copy(
            liveUserText = "",
            transcriptLines = insertOrMergeUserLine(
                lines = state.transcriptLines,
                text = live,
                insertBeforeIndex = assistantBlockStartIndex
                    ?: lateUserInsertIndex(state.transcriptLines),
            ),
        )
    }

    private fun insertOrMergeUserLine(
        lines: List<TranscriptLine>,
        text: String,
        insertBeforeIndex: Int? = null,
    ): List<TranscriptLine> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return lines

        val last = lines.lastOrNull()
        when {
            last?.role == TranscriptRole.User && last.text == trimmed -> return lines
            last?.role == TranscriptRole.User && trimmed.startsWith(last.text) ->
                return lines.dropLast(1) + TranscriptLine(TranscriptRole.User, trimmed)
            last?.role == TranscriptRole.User && last.text.startsWith(trimmed) -> return lines
            insertBeforeIndex != null -> {
                val mergeIndex = (insertBeforeIndex - 1).takeIf { it >= 0 }
                val preceding = mergeIndex?.let { lines.getOrNull(it) }
                if (preceding?.role == TranscriptRole.User) {
                    return mergeUserAtIndex(lines, mergeIndex, trimmed)
                }
                if (insertBeforeIndex <= lines.size) {
                    return lines.toMutableList().apply {
                        add(insertBeforeIndex, TranscriptLine(TranscriptRole.User, trimmed))
                    }
                }
            }
        }
        return lines + TranscriptLine(TranscriptRole.User, trimmed)
    }

    private fun mergeUserAtIndex(
        lines: List<TranscriptLine>,
        index: Int,
        text: String,
    ): List<TranscriptLine> {
        val existing = lines[index]
        if (existing.role != TranscriptRole.User) return lines
        val merged = when {
            existing.text == text -> existing.text
            text.startsWith(existing.text) -> text
            existing.text.startsWith(text) -> existing.text
            else -> text
        }
        if (merged == existing.text) return lines
        return lines.toMutableList().apply {
            set(index, TranscriptLine(TranscriptRole.User, merged))
        }
    }

    private fun lateUserInsertIndex(lines: List<TranscriptLine>): Int? {
        if (lines.isEmpty() || lines.last().role != TranscriptRole.Assistant) return null
        val lastUserIdx = lines.indexOfLast { it.role == TranscriptRole.User }
        if (lastUserIdx < 0) return null
        val assistantsAfterLastUser = lines.size - lastUserIdx - 1
        if (assistantsAfterLastUser <= 0) return null
        return lines.lastIndex
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