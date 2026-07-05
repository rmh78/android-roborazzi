package com.example.roborazzidemo.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface VoiceSessionListener {
    fun onSessionReady()
    fun onStatusChanged(status: String)
    fun onUserSpeechStarted()
    fun onUserSpeechStopped()
    fun onUserTranscriptUpdated(text: String)
    fun onUserTranscriptCompleted(text: String)
    /** Debug/CI inject — always commits immediately, bypassing live-speech deferral. */
    fun onUserTranscriptInjected(text: String)
    fun onAssistantTranscriptDelta(delta: String)
    fun onAssistantTranscriptDone()
    fun onFunctionCall(name: String, arguments: JSONObject, callId: String)
    fun onServerToolExecuted(name: String)
    /** @param recoverable when true the WebSocket stays open (typical API/mic errors). */
    fun onError(message: String, recoverable: Boolean = true)
    fun onDisconnected(reason: String? = null)
}

class GrokVoiceSession(
    private val apiKey: String,
    private val scope: CoroutineScope,
    private val listener: VoiceSessionListener,
    applicationContext: Context,
) {
    private val audioCapture = PcmAudioCapture(applicationContext)
    private val syntheticMicLevel = SyntheticMicLevelAnimator(scope)
    private val audioPlayback = PcmAudioPlayback()
    private val audioRoute = VoiceAudioRoute(applicationContext)
    private val micGate = MicCaptureGate()
    private var webSocket: WebSocket? = null
    private var sessionConfigured = false
    private var sessionUpdateSent = false
    private var responseWatchdogJob: Job? = null
    private var playbackFinalizeJob: Job? = null
    private var activeResponseId: String? = null
    private var audioChunksSent = 0
    private var activeResponseHadAudio = false
    private var pendingDebugText: String? = null
    private var awaitingToolsRestore = false
    private var awaitingGreetingCompletion = false
    private var deferMicResumeForClientTool = false
    private var toolFollowupResponsePending = false
    private var toolFollowupResponseId: String? = null
    private var lastUserTranscriptItemId: String? = null

    val audioLevel: StateFlow<Float> = combine(
        audioCapture.audioLevel,
        syntheticMicLevel.level,
    ) { capturedLevel, syntheticLevel ->
        maxOf(capturedLevel, syntheticLevel)
    }.stateIn(scope, SharingStarted.Eagerly, 0f)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        if (webSocket != null) {
            VoiceLog.w("Session", "connect() ignored — already connected")
            return
        }
        sessionConfigured = false
        sessionUpdateSent = false
        activeResponseId = null
        audioChunksSent = 0
        pendingDebugText = null
        awaitingToolsRestore = false
        awaitingGreetingCompletion = false
        deferMicResumeForClientTool = false
        toolFollowupResponsePending = false
        toolFollowupResponseId = null
        lastUserTranscriptItemId = null
        audioRoute.enterVoiceChat()

        VoiceLog.i("Session", "Connecting to ${VoiceConstants.REALTIME_URL}")

        val request = Request.Builder()
            .url(VoiceConstants.REALTIME_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VoiceLog.i("Session", "WebSocket open (HTTP ${response.code})")
                listener.onStatusChanged("Waiting for conversation…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                VoiceLog.e("Session", "WebSocket failure (HTTP ${response?.code})", t)
                cleanupConnection(
                    notifyListener = true,
                    disconnectReason = t.message ?: "Connection failed",
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                VoiceLog.i("Session", "WebSocket closed: code=$code reason=$reason")
                cleanupConnection(notifyListener = this@GrokVoiceSession.webSocket != null)
            }
        })
    }

    fun disconnect() {
        if (webSocket == null) return
        VoiceLog.i("Session", "Disconnect requested")
        webSocket?.close(1000, "Client disconnect")
        cleanupConnection(notifyListener = true)
    }

    private fun cleanupConnection(notifyListener: Boolean, disconnectReason: String? = null) {
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
        playbackFinalizeJob?.cancel()
        playbackFinalizeJob = null
        audioCapture.stop()
        syntheticMicLevel.cancel()
        audioPlayback.stop()
        webSocket = null
        sessionConfigured = false
        sessionUpdateSent = false
        activeResponseId = null
        pendingDebugText = null
        awaitingToolsRestore = false
        awaitingGreetingCompletion = false
        deferMicResumeForClientTool = false
        toolFollowupResponsePending = false
        toolFollowupResponseId = null
        lastUserTranscriptItemId = null
        audioRoute.exitVoiceChat()
        VoiceLog.d("Session", "Connection cleaned up (audio_chunks_sent=$audioChunksSent)")
        if (notifyListener) {
            listener.onDisconnected(disconnectReason)
        }
    }

    fun pulseMicLevelForSpeech(text: String) {
        syntheticMicLevel.pulseForSpeech(text)
    }

    fun sendSpokenUserMessage(text: String) {
        val socket = webSocket ?: run {
            VoiceLog.w("Session", "sendSpokenUserMessage dropped — socket null")
            return
        }
        if (!sessionConfigured) {
            VoiceLog.w("Session", "sendSpokenUserMessage dropped — session not configured")
            return
        }
        syntheticMicLevel.pulseForDuration(PROCESSING_MIC_PULSE_MS)
        clearResponseWatchdog()
        audioCapture.setMuted(true)
        micGate.pauseForTextInject()
        if (audioCapture.isCapturing()) {
            VoiceLog.clientEvent("input_audio_buffer.clear (before spoken user inject)")
            socket.send(JSONObject().put("type", "input_audio_buffer.clear").toString())
        }
        listener.onUserTranscriptInjected(text)
        sendUserTextTurn(socket, text, logLabel = "spoken user")
    }

    fun sendTextMessage(text: String) {
        val socket = webSocket ?: run {
            VoiceLog.w("Session", "sendTextMessage dropped — socket null")
            return
        }
        if (!sessionConfigured) {
            VoiceLog.w("Session", "sendTextMessage dropped — session not configured")
            return
        }
        clearResponseWatchdog()
        audioCapture.setMuted(true)
        micGate.pauseForTextInject()
        if (audioCapture.isCapturing()) {
            VoiceLog.clientEvent("input_audio_buffer.clear (before text inject)")
            socket.send(JSONObject().put("type", "input_audio_buffer.clear").toString())
        }
        pendingDebugText = text
        VoiceLog.clientEvent("session.update (direct-speech for debug inject)")
        socket.send(VoiceSessionUpdateBuilder.directSpeechForDebugInject().toString())
    }

    /**
     * Server-side session tools (e.g. web_search) can still emit function_call_arguments.done.
     * Acknowledge without response.create — xAI already ran the tool and speech is in the same turn.
     */
    private fun acknowledgeServerExecutedTool(socket: WebSocket, name: String, callId: String) {
        listener.onServerToolExecuted(name)
        VoiceLog.i("Session", "Acknowledging server-executed tool $name (call_id=$callId, no follow-up)")
        VoiceLog.clientEvent("conversation.item.create (server_tool_ack, call_id=$callId)")
        socket.send(
            JSONObject().apply {
                put("type", "conversation.item.create")
                put(
                    "item",
                    JSONObject().apply {
                        put("type", "function_call_output")
                        put("call_id", callId)
                        put("output", """{"status":"completed"}""")
                    },
                )
            }.toString(),
        )
    }

    fun sendToolResult(output: String, callId: String) {
        val socket = webSocket ?: run {
            VoiceLog.w("Session", "sendToolResult dropped — socket null (call_id=$callId)")
            return
        }
        VoiceLog.clientEvent("conversation.item.create (function_call_output, call_id=$callId)")
        VoiceLog.d("Session", "Tool output: ${output.take(200)}")
        socket.send(
            JSONObject().apply {
                put("type", "conversation.item.create")
                put(
                    "item",
                    JSONObject().apply {
                        put("type", "function_call_output")
                        put("call_id", callId)
                        put("output", output)
                    },
                )
            }.toString(),
        )
        toolFollowupResponsePending = true
        VoiceLog.clientEvent("response.create (after tool result)")
        socket.send(JSONObject().put("type", "response.create").toString())
        scheduleResponseWatchdog(socket, WatchdogKind.TOOL_FOLLOWUP)
    }

    private fun sendSessionUpdate(socket: WebSocket) {
        if (sessionUpdateSent) return
        sessionUpdateSent = true
        VoiceLog.clientEvent("session.update")
        socket.send(VoiceSessionUpdateBuilder.withTools().toString())
    }

    private fun sendToolsSessionRestore(socket: WebSocket) {
        VoiceLog.clientEvent("session.update (restore tools after debug inject)")
        socket.send(VoiceSessionUpdateBuilder.withTools().toString())
    }

    private fun sendInitialGreeting(socket: WebSocket) {
        awaitingGreetingCompletion = true
        VoiceLog.clientEvent("response.create (initial greeting)")
        socket.send(
            JSONObject().apply {
                put("type", "response.create")
                put(
                    "response",
                    JSONObject().apply {
                        put("instructions", VoiceSessionUpdateBuilder.initialGreetingInstructions)
                    },
                )
            }.toString(),
        )
        scheduleResponseWatchdog(socket, WatchdogKind.RESPONSE_CREATE)
    }

    private fun sendPendingDebugText(socket: WebSocket, text: String) {
        micGate.holdUntilSpokenDone()
        sendUserTextTurn(socket, text, logLabel = "text")
    }

    private fun sendUserTextTurn(socket: WebSocket, text: String, logLabel: String) {
        VoiceLog.clientEvent("conversation.item.create ($logLabel: $text)")
        socket.send(
            JSONObject().apply {
                put("type", "conversation.item.create")
                put(
                    "item",
                    JSONObject().apply {
                        put("type", "message")
                        put("role", "user")
                        put(
                            "content",
                            org.json.JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("type", "input_text")
                                        put("text", text)
                                    },
                                )
                            },
                        )
                    },
                )
            }.toString(),
        )
        VoiceLog.clientEvent("response.create (after $logLabel message)")
        socket.send(JSONObject().put("type", "response.create").toString())
        scheduleResponseWatchdog(socket, WatchdogKind.RESPONSE_CREATE)
    }

    private fun handleServerEvent(socket: WebSocket, raw: String) {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            VoiceLog.e("Session", "Failed to parse server event", e)
            return
        }

        val type = json.optString("type", "unknown")
        VoiceLog.serverEvent(type, json)

        when (type) {
            "ping" -> {
                val timestamp = json.optLong("timestamp", -1L)
                if (timestamp >= 0L) {
                    VoiceLog.clientEvent("pong (timestamp=$timestamp)")
                    socket.send(
                        JSONObject().apply {
                            put("type", "pong")
                            put("ping_timestamp", timestamp)
                        }.toString(),
                    )
                }
            }
            "session.created" -> Unit
            "conversation.created" -> {
                listener.onStatusChanged("Configuring session…")
                sendSessionUpdate(socket)
            }
            "session.updated" -> {
                val pendingText = pendingDebugText
                when {
                    !sessionConfigured -> {
                        sessionConfigured = true
                        VoiceLog.i("Session", "Session configured — sending initial greeting")
                        listener.onSessionReady()
                        listener.onStatusChanged("Grok is greeting you…")
                        sendInitialGreeting(socket)
                    }
                    pendingText != null -> {
                        pendingDebugText = null
                        sendPendingDebugText(socket, pendingText)
                    }
                    awaitingToolsRestore -> {
                        awaitingToolsRestore = false
                        startAudioCapture()
                    }
                }
            }
            "input_audio_buffer.speech_started" -> {
                if (shouldIgnoreSpeechVad()) {
                    VoiceLog.d(
                        "Session",
                        "Ignoring speech_started (mic_gate=${micGate.state}, " +
                            "response_id=$activeResponseId)",
                    )
                    return
                }
                clearResponseWatchdog()
                lastUserTranscriptItemId = null
                if (!VoiceDeviceHints.useHalfDuplexVoice()) {
                    audioPlayback.flush()
                }
                listener.onUserSpeechStarted()
                listener.onStatusChanged("You are speaking")
            }
            "input_audio_buffer.speech_stopped" -> {
                if (shouldIgnoreSpeechVad()) {
                    VoiceLog.d("Session", "Ignoring speech_stopped (mic_gate=${micGate.state})")
                    return
                }
                listener.onUserSpeechStopped()
                listener.onStatusChanged("Processing…")
            }
            "input_audio_buffer.committed" -> {
                VoiceLog.i("Session", "Audio committed — server VAD will create response")
            }
            "conversation.item.input_audio_transcription.updated" -> {
                val transcript = json.optString("transcript", "")
                if (transcript.isNotBlank()) {
                    listener.onUserTranscriptUpdated(transcript)
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val itemId = json.optString("item_id", "")
                val transcript = json.optString("transcript", "").trim()
                if (transcript.isBlank()) return
                if (itemId.isNotBlank() && itemId == lastUserTranscriptItemId) {
                    VoiceLog.d(
                        "Session",
                        "Ignoring duplicate user transcript completed (item_id=$itemId)",
                    )
                    return
                }
                if (itemId.isNotBlank()) {
                    lastUserTranscriptItemId = itemId
                }
                listener.onUserTranscriptCompleted(transcript)
            }
            "response.created" -> {
                clearResponseWatchdog()
                activeResponseId = json.optJSONObject("response")?.optString("id")
                activeResponseHadAudio = false
                muteMicForEmulatorHalfDuplex()
                if (toolFollowupResponsePending) {
                    toolFollowupResponseId = activeResponseId
                    VoiceLog.i(
                        "Session",
                        "Tool follow-up response started (id=$toolFollowupResponseId)",
                    )
                }
                VoiceLog.i("Session", "Response started (id=$activeResponseId)")
                listener.onStatusChanged("Grok is responding…")
            }
            "response.output_audio.delta",
            "response.audio.delta",
            -> {
                clearResponseWatchdog()
                activeResponseHadAudio = true
                muteMicForEmulatorHalfDuplex()
                audioPlayback.playBase64Chunk(json.optString("delta", ""))
            }
            "response.output_audio_transcript.delta",
            "response.audio_transcript.delta",
            -> {
                val delta = json.optString("delta", "")
                if (delta.isNotEmpty()) {
                    listener.onAssistantTranscriptDelta(delta)
                }
            }
            "response.output_audio_transcript.done",
            "response.audio_transcript.done",
            -> {
                listener.onAssistantTranscriptDone()
            }
            "response.function_call_arguments.done" -> {
                clearResponseWatchdog()
                val name = json.getString("name")
                val callId = json.getString("call_id")
                if (name in SERVER_EXECUTED_TOOLS) {
                    acknowledgeServerExecutedTool(socket, name, callId)
                    return
                }
                deferMicResumeForClientTool = true
                val arguments = JSONObject(json.getString("arguments"))
                listener.onFunctionCall(name, arguments, callId)
            }
            "response.done" -> {
                clearResponseWatchdog()
                val responseId = activeResponseId
                val hadAudio = activeResponseHadAudio
                activeResponseId = null
                activeResponseHadAudio = false
                val isToolFollowupCompletion =
                    toolFollowupResponsePending && responseId == toolFollowupResponseId
                VoiceLog.i(
                    "Session",
                    "Response complete (id=$responseId, had_audio=$hadAudio, " +
                        "defer_tool=$deferMicResumeForClientTool, " +
                        "tool_followup_pending=$toolFollowupResponsePending, " +
                        "tool_followup_id=$toolFollowupResponseId, " +
                        "is_tool_followup_done=$isToolFollowupCompletion)",
                )
                if (sessionConfigured) {
                    if (awaitingGreetingCompletion) {
                        awaitingGreetingCompletion = false
                        listener.onStatusChanged("Preparing microphone…")
                        startAudioCaptureAfterPlaybackIdle()
                    } else if (
                        deferMicResumeForClientTool &&
                        toolFollowupResponsePending &&
                        !isToolFollowupCompletion
                    ) {
                        // Tool-call response finished before the follow-up response completes.
                        deferMicResumeForClientTool = false
                        VoiceLog.d(
                            "Session",
                            "Tool-call response done — waiting for follow-up speech",
                        )
                    } else if (deferMicResumeForClientTool && !toolFollowupResponsePending) {
                        listener.onStatusChanged("Running tool…")
                    } else {
                        if (isToolFollowupCompletion) {
                            deferMicResumeForClientTool = false
                            toolFollowupResponsePending = false
                            toolFollowupResponseId = null
                        }
                        resumeListeningAfterResponse(hadAudio)
                    }
                }
            }
            "error" -> {
                clearResponseWatchdog()
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("error")
                    ?: json.optString("message", "Unknown voice API error")
                if (isBenignApiError(message)) {
                    VoiceLog.w("Session", "Benign API error (ignored): $message")
                    return
                }
                VoiceLog.e("Session", "API error: $message")
                listener.onError(message, recoverable = true)
            }
            else -> VoiceLog.d("Session", "Unhandled event type: $type")
        }
    }

    /**
     * Emulator half-duplex: stop streaming mic audio while Grok speaks (no AEC on AVD).
     * Never sends [input_audio_buffer.clear] — server VAD owns committed audio.
     */
    private fun muteMicForEmulatorHalfDuplex() {
        if (!VoiceDeviceHints.useHalfDuplexVoice() || !audioCapture.isCapturing() || audioCapture.isMuted()) {
            return
        }
        audioCapture.setMuted(true)
        VoiceLog.d("Session", "Emulator half-duplex: mic muted during assistant speech")
    }

    private fun shouldIgnoreSpeechVad(): Boolean =
        micGate.state != MicCaptureGate.State.Streaming ||
            (VoiceDeviceHints.useHalfDuplexVoice() && activeResponseId != null) ||
            (VoiceDeviceHints.useHalfDuplexVoice() && !audioPlayback.isIdle())

    private fun resumeMicAfterEmulatorHalfDuplex() {
        if (!VoiceDeviceHints.useHalfDuplexVoice() || !audioCapture.isMuted()) {
            return
        }
        audioCapture.setMuted(false)
        VoiceLog.d("Session", "Emulator half-duplex: mic resumed for user turn")
    }

    private fun resumeMicAfterInject(socket: WebSocket, hadAudio: Boolean) {
        when {
            micGate.shouldRestoreToolsAfterDirectSpeech(hadAudio) -> {
                micGate.onCaptureResumed()
                awaitingToolsRestore = true
                sendToolsSessionRestore(socket)
            }
            micGate.shouldResumeMicAfterSpokenInject() || !audioCapture.isCapturing() -> {
                micGate.onCaptureResumed()
                startAudioCapture()
            }
        }
    }

    private fun startAudioCaptureAfterPlaybackIdle() {
        audioPlayback.whenIdle(
            onIdle = { startAudioCapture() },
            postDrainDelayMs = emulatorPlaybackTailMs(),
        )
    }

    private fun emulatorPlaybackTailMs(): Long =
        if (VoiceDeviceHints.isLikelyEmulator()) EMULATOR_PLAYBACK_TAIL_MS else 0L

    private fun startAudioCapture() {
        scope.launch(Dispatchers.IO) {
            try {
                if (audioCapture.isCapturing()) {
                    audioCapture.setMuted(false)
                    micGate.onCaptureStarted()
                    listener.onStatusChanged("Listening — ask a question")
                    VoiceLog.i("Session", "Mic unmuted (capture already active)")
                    return@launch
                }
                audioCapture.start(
                    onChunk = { base64Chunk ->
                        audioChunksSent++
                        if (audioChunksSent == 1 || audioChunksSent % AUDIO_CHUNK_LOG_INTERVAL == 0) {
                            VoiceLog.d("Session", "Streaming mic audio (chunks_sent=$audioChunksSent)")
                        }
                        webSocket?.send(
                            JSONObject().apply {
                                put("type", "input_audio_buffer.append")
                                put("audio", base64Chunk)
                            }.toString(),
                        )
                    },
                    onFailure = { message ->
                        listener.onError(message, recoverable = true)
                    },
                )
                micGate.onCaptureStarted()
                listener.onStatusChanged("Listening — ask a question")
                VoiceLog.i("Session", "Mic streaming active (20ms PCM16 chunks)")
            } catch (e: Exception) {
                VoiceLog.e("Session", "Audio capture failed", e)
                listener.onError(e.message ?: "Microphone capture failed", recoverable = true)
            }
        }
    }

    private fun isBenignApiError(message: String): Boolean =
        message.contains("Cancellation failed", ignoreCase = true) ||
            message.contains("no active response found", ignoreCase = true)

    private fun resumeListeningAfterResponse(hadAudio: Boolean) {
        playbackFinalizeJob?.cancel()
        playbackFinalizeJob = null
        var finalized = false
        fun finalizeResponse() {
            if (finalized) return
            finalized = true
            playbackFinalizeJob?.cancel()
            playbackFinalizeJob = null
            resumeMicAfterEmulatorHalfDuplex()
            if (micGate.shouldResumeMicAfterSpokenInject()) {
                micGate.onCaptureResumed()
            }
            if (!VoiceDeviceHints.useHalfDuplexVoice() && audioCapture.isMuted()) {
                audioCapture.setMuted(false)
            }
            listener.onStatusChanged(
                if (hadAudio) "Listening — ask a question" else "Listening",
            )
        }
        if (hadAudio || !audioPlayback.isIdle()) {
            audioPlayback.whenIdle(
                onIdle = { finalizeResponse() },
                postDrainDelayMs = emulatorPlaybackTailMs(),
            )
            playbackFinalizeJob = scope.launch {
                delay(PLAYBACK_FINALIZE_TIMEOUT_MS)
                if (!finalized) {
                    VoiceLog.w(
                        "Session",
                        "Playback finalize watchdog fired — forcing listen state",
                    )
                    audioPlayback.stop()
                    finalizeResponse()
                }
            }
        } else {
            finalizeResponse()
        }
    }

    private fun scheduleResponseWatchdog(socket: WebSocket, kind: WatchdogKind) {
        responseWatchdogJob?.cancel()
        val timeoutMs = when (kind) {
            WatchdogKind.RESPONSE_CREATE -> RESPONSE_CREATE_TIMEOUT_MS
            WatchdogKind.TOOL_FOLLOWUP -> TOOL_FOLLOWUP_TIMEOUT_MS
        }
        VoiceLog.d("Session", "Response watchdog started (${timeoutMs}ms, kind=$kind)")
        responseWatchdogJob = scope.launch {
            delay(timeoutMs)
            VoiceLog.w(
                "Session",
                "Response watchdog fired (kind=$kind, response_id=$activeResponseId, " +
                    "chunks_sent=$audioChunksSent)",
            )
            activeResponseId?.let { responseId ->
                VoiceLog.clientEvent("response.cancel (response_id=$responseId)")
                socket.send(
                    JSONObject().apply {
                        put("type", "response.cancel")
                        put("response_id", responseId)
                    }.toString(),
                )
            }
            activeResponseId = null
            listener.onStatusChanged("Listening — ask a question")
            resumeMicAfterEmulatorHalfDuplex()
            if (micGate.shouldResumeMicAfterSpokenInject()) {
                micGate.onCaptureResumed()
            }
            if (!VoiceDeviceHints.useHalfDuplexVoice() && audioCapture.isMuted()) {
                audioCapture.setMuted(false)
            }
        }
    }

    private fun clearResponseWatchdog() {
        if (responseWatchdogJob != null) {
            VoiceLog.d("Session", "Response watchdog cleared")
        }
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
    }

    private enum class WatchdogKind {
        /** After client sends response.create (greeting, text/spoken inject). */
        RESPONSE_CREATE,
        /** After client sends response.create following a function_call_output. */
        TOOL_FOLLOWUP,
    }

    companion object {
        private const val RESPONSE_CREATE_TIMEOUT_MS = 20_000L
        private const val TOOL_FOLLOWUP_TIMEOUT_MS = 60_000L
        private const val PLAYBACK_FINALIZE_TIMEOUT_MS = 45_000L
        private const val EMULATOR_PLAYBACK_TAIL_MS = 450L
        private const val AUDIO_CHUNK_LOG_INTERVAL = 250
        private const val PROCESSING_MIC_PULSE_MS = 1_500L

        /** Tools configured with type-only entries in session.update; executed by xAI, not the client. */
        private val SERVER_EXECUTED_TOOLS = setOf("web_search", "x_search", "file_search")
    }
}