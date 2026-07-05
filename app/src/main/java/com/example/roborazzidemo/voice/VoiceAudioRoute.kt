package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioManager

/**
 * Routes playback and capture through the voice-communication audio path on physical devices.
 * Skipped on emulators where IN_COMMUNICATION routing often silences the virtual microphone.
 */
class VoiceAudioRoute(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode: Int? = null
    private var active = false

    fun enterVoiceChat() {
        if (active || !VoiceDeviceHints.useVoiceCommunicationRoute()) {
            VoiceLog.i(
                "AudioRoute",
                if (VoiceDeviceHints.isLikelyEmulator()) {
                    "Emulator detected — using default audio route for microphone capture"
                } else {
                    "Voice chat route already active"
                },
            )
            return
        }
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true
        active = true
        @Suppress("DEPRECATION")
        val speakerphone = audioManager.isSpeakerphoneOn
        VoiceLog.i(
            "AudioRoute",
            "Voice chat route active (mode=IN_COMMUNICATION, speakerphone=$speakerphone)",
        )
    }

    fun exitVoiceChat() {
        if (!active) return
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        previousMode?.let { audioManager.mode = it }
        previousMode = null
        active = false
        VoiceLog.i("AudioRoute", "Voice chat route restored")
    }
}