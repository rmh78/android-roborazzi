package com.example.roborazzidemo.voice

import android.media.MediaRecorder
import org.json.JSONObject

/**
 * Device-specific voice strategy.
 *
 * Physical devices follow the [xAI Android demo](https://github.com/xai-org/xai-cookbook/tree/main/Android/VoiceApiAndroidExample):
 * continuous full-duplex capture, `VOICE_COMMUNICATION` + platform AEC/NS/AGC, server VAD defaults.
 *
 * Emulators lack working AEC, so the app uses half-duplex (mute mic while Grok speaks),
 * multi-source capture fallback, and slightly stricter server VAD.
 */
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

    /** Platform AEC/NS/AGC often claim available but break virtual-mic levels on AVDs. */
    fun shouldAttachPlatformEffects(): Boolean = !isLikelyEmulator()

    /**
     * Emulator half-duplex: mute uplink while assistant audio plays to prevent
     * host-speaker → host-mic echo from re-entering server VAD.
     * Physical devices stay full-duplex (xAI demo / hardware AEC).
     */
    fun muteMicWhileAssistantSpeaks(): Boolean = isLikelyEmulator()

    /**
     * Extra silence after AudioTrack drain so residual speaker audio does not
     * hit the virtual mic on unmute. Devices use 0 (full duplex).
     */
    fun playbackTailMs(): Long = if (isLikelyEmulator()) 450L else 0L

    /**
     * Server VAD config. Devices use API defaults (xAI demo: `type=server_vad` only).
     * Emulators raise threshold and silence window to reduce false commits from noise/bleed.
     *
     * @see <a href="https://docs.x.ai/developers/model-capabilities/audio/voice-agent">Voice Agent API</a>
     */
    fun turnDetection(): JSONObject =
        JSONObject().apply {
            put("type", "server_vad")
            if (isLikelyEmulator()) {
                put("threshold", 0.7)
                put("prefix_padding_ms", 300)
                put("silence_duration_ms", 500)
            }
        }
}