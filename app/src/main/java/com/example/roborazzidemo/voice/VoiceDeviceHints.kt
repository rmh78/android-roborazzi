package com.example.roborazzidemo.voice

import android.media.MediaRecorder
import android.os.Build

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

    fun preferredCaptureSources(): List<Int> =
        if (isLikelyEmulator()) {
            listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            )
        } else {
            listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
            )
        }

    fun useVoiceCommunicationRoute(): Boolean = !isLikelyEmulator()
}