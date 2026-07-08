package com.example.roborazzidemo.voice

import android.content.Context
import android.media.AudioManager

/**
 * Routes playback and capture through [AudioManager.MODE_IN_COMMUNICATION] on physical
 * devices. Skipped on emulators — IN_COMMUNICATION often silences the AVD virtual mic.
 * The xAI Android demo does not change audio mode; it relies on [VOICE_COMMUNICATION] only.
 */
class VoiceAudioRoute(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode: Int? = null
    private var active = false

    fun enterVoiceChat() {
        if (active) {
            VoiceLog.i("AudioRoute", "Voice chat route already active")
            return
        }
        if (VoiceDeviceHints.isLikelyEmulator()) {
            VoiceLog.i(
                "AudioRoute",
                "Emulator detected — keeping default audio route (xAI demo pattern)",
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