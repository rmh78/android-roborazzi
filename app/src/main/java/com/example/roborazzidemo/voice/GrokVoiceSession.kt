package com.example.roborazzidemo.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    fun onAssistantTranscriptDelta(delta: String)
    fun onAssistantTranscriptDone()
    fun onFunctionCall(name: String, arguments: JSONObject, callId: String)
    fun onError(message: String)
    fun onDisconnected()
}

class GrokVoiceSession(
    private val apiKey: String,
    private val scope: CoroutineScope,
    private val listener: VoiceSessionListener,
) {
    private val audioCapture = PcmAudioCapture()
    private val audioPlayback = PcmAudioPlayback()
    private var webSocket: WebSocket? = null
    private var sessionConfigured = false
    private var sessionUpdateSent = false
    private var responseWatchdogJob: Job? = null
    private var activeResponseId: String? = null
    private var audioChunksSent = 0

    val audioLevel = audioCapture.audioLevel

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

        VoiceLog.i("Session", "Connecting to ${VoiceConstants.REALTIME_URL}")

        val request = Request.Builder()
            .url(VoiceConstants.REALTIME_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VoiceLog.i("Session", "WebSocket open (HTTP ${response.code})")
                listener.onStatusChanged("Configuring session…")
                sendSessionUpdate(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                VoiceLog.e("Session", "WebSocket failure (HTTP ${response?.code})", t)
                listener.onError(t.message ?: "Connection failed")
                cleanupConnection(notifyListener = true)
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

    private fun cleanupConnection(notifyListener: Boolean) {
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
        audioCapture.stop()
        audioPlayback.stop()
        webSocket = null
        sessionConfigured = false
        sessionUpdateSent = false
        activeResponseId = null
        VoiceLog.d("Session", "Connection cleaned up (audio_chunks_sent=$audioChunksSent)")
        if (notifyListener) {
            listener.onDisconnected()
        }
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
        VoiceLog.clientEvent("response.create (after tool result)")
        socket.send(JSONObject().put("type", "response.create").toString())
        scheduleResponseWatchdog(socket)
    }

    private fun sendSessionUpdate(socket: WebSocket) {
        if (sessionUpdateSent) return
        sessionUpdateSent = true
        VoiceLog.clientEvent("session.update")
        socket.send(VoiceToolDefinitions.sessionUpdateJson().toString())
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
            "session.created", "conversation.created" -> Unit
            "session.updated" -> {
                if (!sessionConfigured) {
                    sessionConfigured = true
                    VoiceLog.i("Session", "Session configured — starting audio capture")
                    listener.onSessionReady()
                    listener.onStatusChanged("Ready…")
                    startAudioCaptureAfterWarmup()
                }
            }
            "input_audio_buffer.speech_started" -> {
                clearResponseWatchdog()
                listener.onUserSpeechStarted()
                listener.onStatusChanged("You are speaking")
            }
            "input_audio_buffer.speech_stopped" -> {
                listener.onUserSpeechStopped()
                listener.onStatusChanged("Processing…")
            }
            "input_audio_buffer.committed" -> {
                VoiceLog.i("Session", "Audio committed — waiting for response")
                scheduleResponseWatchdog(socket)
            }
            "conversation.item.input_audio_transcription.updated" -> {
                val transcript = json.optString("transcript", "")
                if (transcript.isNotBlank()) {
                    listener.onUserTranscriptUpdated(transcript)
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = json.optString("transcript", "")
                if (transcript.isNotBlank()) {
                    listener.onUserTranscriptCompleted(transcript)
                }
            }
            "response.created" -> {
                clearResponseWatchdog()
                activeResponseId = json.optJSONObject("response")?.optString("id")
                VoiceLog.i("Session", "Response started (id=$activeResponseId)")
                listener.onStatusChanged("Grok is responding…")
            }
            "response.output_audio.delta" -> {
                clearResponseWatchdog()
                audioPlayback.playBase64Chunk(json.optString("delta", ""))
            }
            "response.output_audio_transcript.delta" -> {
                val delta = json.optString("delta", "")
                if (delta.isNotEmpty()) {
                    listener.onAssistantTranscriptDelta(delta)
                }
            }
            "response.output_audio_transcript.done" -> {
                listener.onAssistantTranscriptDone()
            }
            "response.function_call_arguments.done" -> {
                clearResponseWatchdog()
                val name = json.getString("name")
                val arguments = JSONObject(json.getString("arguments"))
                val callId = json.getString("call_id")
                listener.onFunctionCall(name, arguments, callId)
            }
            "response.done" -> {
                clearResponseWatchdog()
                activeResponseId = null
                VoiceLog.i("Session", "Response complete")
                if (sessionConfigured) {
                    listener.onStatusChanged("Listening")
                }
            }
            "error" -> {
                clearResponseWatchdog()
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("error")
                    ?: json.optString("message", "Unknown voice API error")
                VoiceLog.e("Session", "API error: $message")
                listener.onError(message)
            }
            else -> VoiceLog.d("Session", "Unhandled event type: $type")
        }
    }

    private fun startAudioCaptureAfterWarmup() {
        try {
            audioCapture.start(scope) { base64Chunk ->
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
            }
            listener.onStatusChanged("Listening")
            VoiceLog.i(
                "Session",
                "Mic streaming active (warmup=${PcmAudioCapture.CAPTURE_WARMUP_MS}ms, " +
                    "chunk=${PcmAudioCapture.FRAME_SIZE_BYTES}B)",
            )
        } catch (e: Exception) {
            VoiceLog.e("Session", "Audio capture failed", e)
            listener.onError(e.message ?: "Microphone capture failed")
        }
    }

    private fun scheduleResponseWatchdog(socket: WebSocket) {
        responseWatchdogJob?.cancel()
        VoiceLog.d("Session", "Response watchdog started (${RESPONSE_TIMEOUT_MS}ms)")
        responseWatchdogJob = scope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            VoiceLog.w(
                "Session",
                "Response timeout — cancelling (response_id=$activeResponseId, chunks_sent=$audioChunksSent)",
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
            VoiceLog.clientEvent("input_audio_buffer.clear")
            socket.send(JSONObject().put("type", "input_audio_buffer.clear").toString())
            activeResponseId = null
            listener.onStatusChanged("Listening")
        }
    }

    private fun clearResponseWatchdog() {
        if (responseWatchdogJob != null) {
            VoiceLog.d("Session", "Response watchdog cleared")
        }
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
    }

    companion object {
        private const val RESPONSE_TIMEOUT_MS = 20_000L
        private const val AUDIO_CHUNK_LOG_INTERVAL = 50
    }
}