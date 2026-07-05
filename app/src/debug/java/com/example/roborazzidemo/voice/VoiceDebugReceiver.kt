package com.example.roborazzidemo.voice

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VoiceDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_VOICE_TEST_WARMUP -> TestSpeechSpeaker.warmUp(context)
            ACTION_VOICE_TEST_ANNOUNCE -> handleTestAnnounce(context, intent)
            ACTION_VOICE_TEXT -> handleVoiceText(intent)
            ACTION_VOICE_SPOKEN -> handleVoiceSpoken(intent)
            ACTION_VOICE_DISCONNECT -> handleDisconnect()
            else -> Unit
        }
    }

    private fun handleTestAnnounce(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            VoiceLog.w("Debug", "VOICE_TEST_ANNOUNCE broadcast missing text extra")
            return
        }
        val pending = goAsync()
        TestSpeechSpeaker.speak(context, text) { success ->
            if (success) {
                pending.setResultCode(Activity.RESULT_OK)
            }
            pending.finish()
        }
    }

    private fun handleVoiceText(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            VoiceLog.w("Debug", "VOICE_TEXT broadcast missing text extra")
            return
        }
        val accepted = VoiceDebugBridge.dispatch(text)
        if (accepted) {
            VoiceLog.i("Debug", "Injected text command: $text")
        } else {
            VoiceLog.w("Debug", "Text command dropped — voice session not connected")
        }
    }

    private fun handleVoiceSpoken(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            VoiceLog.w("Debug", "VOICE_SPOKEN broadcast missing text extra")
            return
        }
        val accepted = VoiceDebugBridge.dispatchSpoken(text)
        if (accepted) {
            VoiceLog.i("Debug", "Injected spoken user message: $text")
        } else {
            VoiceLog.w("Debug", "Spoken user message dropped — voice session not connected")
        }
    }

    private fun handleDisconnect() {
        val accepted = VoiceDebugBridge.disconnect()
        if (accepted) {
            VoiceLog.i("Debug", "Disconnect command accepted")
        } else {
            VoiceLog.w("Debug", "Disconnect dropped — voice session not connected")
        }
    }

    companion object {
        const val ACTION_VOICE_TEST_WARMUP = "com.example.roborazzidemo.VOICE_TEST_WARMUP"
        const val ACTION_VOICE_TEST_ANNOUNCE = "com.example.roborazzidemo.VOICE_TEST_ANNOUNCE"
        const val ACTION_VOICE_TEXT = "com.example.roborazzidemo.VOICE_TEXT"
        const val ACTION_VOICE_SPOKEN = "com.example.roborazzidemo.VOICE_SPOKEN"
        const val ACTION_VOICE_DISCONNECT = "com.example.roborazzidemo.VOICE_DISCONNECT"
        const val EXTRA_TEXT = "text"
    }
}