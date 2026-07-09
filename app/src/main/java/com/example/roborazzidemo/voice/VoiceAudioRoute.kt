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
    private var raisedMusicVolume = false

    fun enterVoiceChat() {
        if (active) {
            VoiceLog.i("AudioRoute", "Voice chat route already active")
            return
        }
        // Playback uses USAGE_MEDIA; AVD music stream is often 0 after cold boot.
        ensureMediaStreamAudible()
        if (VoiceDeviceHints.isLikelyEmulator()) {
            active = true
            VoiceLog.i(
                "AudioRoute",
                "Emulator detected — default route + media stream volume check",
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
        if (!VoiceDeviceHints.isLikelyEmulator()) {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            previousMode?.let { audioManager.mode = it }
            previousMode = null
        }
        active = false
        raisedMusicVolume = false
        VoiceLog.i("AudioRoute", "Voice chat route restored")
    }

    private fun ensureMediaStreamAudible() {
        try {
            val stream = AudioManager.STREAM_MUSIC
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            if (max > 0 && current < (max * 2 / 3)) {
                val target = (max * 3 / 4).coerceAtLeast(1)
                audioManager.setStreamVolume(stream, target, 0)
                raisedMusicVolume = true
                VoiceLog.i(
                    "AudioRoute",
                    "Raised STREAM_MUSIC volume $current → $target (max=$max)",
                )
            } else {
                VoiceLog.i("AudioRoute", "STREAM_MUSIC volume ok (current=$current, max=$max)")
            }
        } catch (e: SecurityException) {
            VoiceLog.w("AudioRoute", "Could not adjust media volume: ${e.message}")
        }
    }
}