package com.example.roborazzidemo.voice

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64

class VoiceDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_VOICE_TEST_WARMUP -> TestPcmSpeechGenerator.warmUp(context)
            ACTION_VOICE_PCM_SPEAK -> handleVoicePcmSpeak(context, intent)
            ACTION_VOICE_PCM_BYTES -> handleVoicePcmBytes(context, intent)
            ACTION_VOICE_TEXT -> handleVoiceText(intent)
            ACTION_VOICE_DISCONNECT -> handleDisconnect()
            else -> Unit
        }
    }

    private fun handleVoicePcmSpeak(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            VoiceLog.w("Debug", "VOICE_PCM_SPEAK broadcast missing text extra")
            return
        }
        val pending = goAsync()
        TestPcmSpeechGenerator.generate(context, text) { pcm ->
            if (pcm == null) {
                VoiceLog.w("Debug", "PCM synthesis failed for: $text")
                pending.finish()
                return@generate
            }
            dispatchPcmUtterance(
                context = context,
                intent = intent,
                pcm = pcm,
                pending = pending,
                successLog = "PCM utterance streamed for: $text",
                failureLog = "PCM utterance dropped for: $text",
            )
        }
    }

    private fun handleVoicePcmBytes(context: Context, intent: Intent) {
        val encoded = intent.getStringExtra(EXTRA_PCM)?.trim().orEmpty()
        if (encoded.isEmpty()) {
            VoiceLog.w("Debug", "VOICE_PCM_BYTES broadcast missing pcm extra")
            return
        }
        val pcm = Base64.decode(encoded, Base64.DEFAULT)
        val pending = goAsync()
        dispatchPcmUtterance(
            context = context,
            intent = intent,
            pcm = pcm,
            pending = pending,
            successLog = "Raw PCM utterance streamed (${pcm.size} bytes)",
            failureLog = "Raw PCM utterance dropped — voice session not connected",
        )
    }

    private fun dispatchPcmUtterance(
        context: Context,
        intent: Intent,
        pcm: ByteArray,
        pending: BroadcastReceiver.PendingResult,
        successLog: String,
        failureLog: String,
    ) {
        if (intent.getBooleanExtra(EXTRA_MIRROR_PCM, false)) {
            VoiceDebugBridge.pcmChunkMirror = TestPcmMirrorPlayback.create(context)
            VoiceLog.i("Debug", "User PCM mirror playback enabled for E2E utterance")
        }
        val accepted = VoiceDebugBridge.dispatchPcm(pcm) {
            VoiceLog.i("Debug", successLog)
            pending.setResultCode(Activity.RESULT_OK)
            pending.finish()
        }
        if (!accepted) {
            VoiceDebugBridge.releasePcmChunkMirror()
            VoiceLog.w("Debug", failureLog)
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
        const val ACTION_VOICE_PCM_SPEAK = "com.example.roborazzidemo.VOICE_PCM_SPEAK"
        const val ACTION_VOICE_PCM_BYTES = "com.example.roborazzidemo.VOICE_PCM_BYTES"
        const val ACTION_VOICE_TEXT = "com.example.roborazzidemo.VOICE_TEXT"
        const val ACTION_VOICE_DISCONNECT = "com.example.roborazzidemo.VOICE_DISCONNECT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PCM = "pcm"
        const val EXTRA_MIRROR_PCM = "mirror_pcm"
    }
}