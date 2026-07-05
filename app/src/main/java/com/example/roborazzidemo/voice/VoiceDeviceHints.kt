package com.example.roborazzidemo.voice

import android.media.MediaRecorder
import android.os.Build
import org.json.JSONObject

object VoiceDeviceHints {
    fun isLikelyEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)

    /** xAI demo skips IN_COMMUNICATION routing on emulators where it silences the virtual mic. */
    fun useVoiceCommunicationRoute(): Boolean = !isLikelyEmulator()

    /**
     * Physical devices: [MediaRecorder.AudioSource.VOICE_COMMUNICATION] for AEC (xAI demo).
     * Emulators: [MediaRecorder.AudioSource.MIC] first — VOICE_COMMUNICATION often returns silence.
     */
    fun preferredCaptureSources(): List<Int> =
        if (isLikelyEmulator()) {
            listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            )
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }

    /** Emulators use half-duplex (mic off during assistant speech) — no AEC, no barge-in. */
    fun useHalfDuplexVoice(): Boolean = isLikelyEmulator()

    fun turnDetection(): JSONObject =
        JSONObject().apply {
            put("type", "server_vad")
        }
}