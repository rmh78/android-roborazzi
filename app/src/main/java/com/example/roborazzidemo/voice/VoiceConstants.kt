package com.example.roborazzidemo.voice

object VoiceConstants {
    const val SAMPLE_RATE_HZ = 24_000
    const val PCM_FRAME_DURATION_MS = 20
    const val PCM_FRAME_BYTES = SAMPLE_RATE_HZ * PCM_FRAME_DURATION_MS / 1000 * 2
    const val VOICE_MODEL = "grok-voice-latest"
    const val REALTIME_URL = "wss://api.x.ai/v1/realtime"
    const val VOICE_ID = "eve"
}