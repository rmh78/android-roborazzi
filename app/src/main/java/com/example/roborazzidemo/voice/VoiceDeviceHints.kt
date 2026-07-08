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

    fun useVoiceCommunicationRoute(): Boolean = true

    fun preferredCaptureSource(): Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION

    fun turnDetection(): JSONObject =
        JSONObject().apply {
            put("type", "server_vad")
        }
}