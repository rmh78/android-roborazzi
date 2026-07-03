package com.example.roborazzidemo.voice

import android.util.Log
import kotlinx.coroutines.CoroutineScope
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

    val audioLevel = audioCapture.audioLevel

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        if (webSocket != null) return

        val request = Request.Builder()
            .url(VoiceConstants.REALTIME_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket open")
                listener.onStatusChanged("Connecting…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                listener.onError(t.message ?: "Connection failed")
                disconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                listener.onDisconnected()
            }
        })
    }

    fun disconnect() {
        audioCapture.stop()
        audioPlayback.stop()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        sessionConfigured = false
        listener.onDisconnected()
    }

    fun sendToolResult(output: String, callId: String) {
        val socket = webSocket ?: return
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
        socket.send(JSONObject().put("type", "response.create").toString())
    }

    private fun handleServerEvent(raw: String) {
        val json = JSONObject(raw)
        when (json.getString("type")) {
            "conversation.created" -> {
                webSocket?.send(VoiceToolDefinitions.sessionUpdateJson().toString())
            }
            "session.updated" -> {
                sessionConfigured = true
                listener.onSessionReady()
                listener.onStatusChanged("Listening")
                startAudioCapture()
            }
            "input_audio_buffer.speech_started" -> {
                listener.onUserSpeechStarted()
                listener.onStatusChanged("You are speaking")
            }
            "input_audio_buffer.speech_stopped" -> {
                listener.onUserSpeechStopped()
                listener.onStatusChanged("Processing…")
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
            "response.output_audio.delta" -> {
                val delta = json.optString("delta", "")
                audioPlayback.playBase64Chunk(delta)
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
                val name = json.getString("name")
                val arguments = JSONObject(json.getString("arguments"))
                val callId = json.getString("call_id")
                listener.onFunctionCall(name, arguments, callId)
            }
            "response.done" -> {
                if (sessionConfigured) {
                    listener.onStatusChanged("Listening")
                }
            }
            "error" -> {
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message", "Unknown voice API error")
                listener.onError(message)
            }
        }
    }

    private fun startAudioCapture() {
        audioCapture.start(scope) { base64Chunk ->
            webSocket?.send(
                JSONObject().apply {
                    put("type", "input_audio_buffer.append")
                    put("audio", base64Chunk)
                }.toString(),
            )
        }
    }

    companion object {
        private const val TAG = "GrokVoiceSession"
    }
}