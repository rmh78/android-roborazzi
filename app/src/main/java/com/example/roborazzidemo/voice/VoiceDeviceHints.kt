package com.example.roborazzidemo.voice

import android.media.MediaRecorder
import org.json.JSONObject

object VoiceDeviceHints {
    fun isLikelyEmulator(): Boolean =
        android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Emulator", ignoreCase = true) ||
            android.os.Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
            android.os.Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            android.os.Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            android.os.Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)

    /**
     * xAI demo uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] only.
     * On emulators, fall back through additional sources when the virtual mic is silent.
     */
    fun captureSources(): List<Int> =
        if (isLikelyEmulator()) {
            listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
            )
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }

    fun turnDetection(): JSONObject =
        JSONObject().apply {
            put("type", "server_vad")
        }
}