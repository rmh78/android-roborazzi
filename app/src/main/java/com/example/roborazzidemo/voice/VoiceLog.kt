package com.example.roborazzidemo.voice

import android.util.Log
import org.json.JSONObject

object VoiceLog {
    const val TAG = "VoiceAssistant"

    fun d(component: String, message: String) {
        Log.d(TAG, "[$component] $message")
    }

    fun i(component: String, message: String) {
        Log.i(TAG, "[$component] $message")
    }

    fun w(component: String, message: String) {
        Log.w(TAG, "[$component] $message")
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$component] $message", throwable)
        } else {
            Log.e(TAG, "[$component] $message")
        }
    }

    fun serverEvent(type: String, json: JSONObject) {
        val detail = describeServerEvent(type, json)
        if (detail.isEmpty()) {
            Log.d(TAG, "[Session] ← $type")
        } else {
            Log.d(TAG, "[Session] ← $type | $detail")
        }
    }

    fun clientEvent(message: String) {
        Log.d(TAG, "[Session] → $message")
    }

    fun ui(message: String) {
        Log.i(TAG, "[UI] $message")
    }

    private fun describeServerEvent(type: String, json: JSONObject): String = when (type) {
        "ping" -> "timestamp=${json.optLong("timestamp")}"
        "session.updated" -> "voice=${json.optJSONObject("session")?.optString("voice")}"
        "input_audio_buffer.speech_started" -> "item_id=${json.optString("item_id")}"
        "input_audio_buffer.speech_stopped" -> "item_id=${json.optString("item_id")}"
        "input_audio_buffer.committed" -> "item_id=${json.optString("item_id")}"
        "conversation.item.input_audio_transcription.updated",
        "conversation.item.input_audio_transcription.completed",
        -> "transcript=${truncate(json.optString("transcript"))}"
        "response.created" -> "response_id=${json.optJSONObject("response")?.optString("id")}"
        "response.output_audio.delta",
        "response.audio.delta",
        -> "bytes=${json.optString("delta").length}"
        "response.output_audio_transcript.delta",
        "response.audio_transcript.delta",
        -> "delta=${truncate(json.optString("delta"))}"
        "response.output_audio_transcript.done",
        "response.audio_transcript.done",
        -> "transcript=${truncate(json.optString("transcript"))}"
        "response.function_call_arguments.done" -> buildString {
            append("tool=${json.optString("name")}")
            append(" call_id=${json.optString("call_id")}")
            append(" args=${truncate(json.optString("arguments"))}")
        }
        "response.done" -> "response_id=${json.optJSONObject("response")?.optString("id")}"
        "error" -> json.optJSONObject("error")?.optString("message")
            ?: json.optString("message", json.optString("error"))
        else -> ""
    }

    private fun truncate(value: String, maxLength: Int = 120): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength) + "…"
    }
}