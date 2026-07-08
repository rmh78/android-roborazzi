package com.example.roborazzidemo.voice.support

import androidx.test.platform.app.InstrumentationRegistry

object VoiceE2eConfig {
    /** When true, run a minimal connect → tool → nav → disconnect path (~1 min). */
    fun isShortMode(): Boolean =
        InstrumentationRegistry.getArguments().getString("voiceE2eShort") == "true"
}