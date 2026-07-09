package com.example.roborazzidemo.voice

object VoiceConstants {
    const val SAMPLE_RATE_HZ = 24_000
    const val VOICE_MODEL = "grok-voice-latest"
    val REALTIME_URL = "wss://api.x.ai/v1/realtime?model=$VOICE_MODEL"
    const val DEFAULT_VOICE_ID = "eve"
}