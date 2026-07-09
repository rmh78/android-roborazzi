package com.example.roborazzidemo.voice

/**
 * Debug-only hook so emulator/CI E2E can drive the voice session with text injects.
 *
 * [skipLiveCapture] is the stable E2E path: no AudioRecord, no user TTS/beep, no mute thrash —
 * only WebSocket + assistant playback + [dispatchSpoken] injects.
 */
object VoiceDebugBridge {
    @Volatile
    var sendTextCommand: ((String) -> Unit)? = null

    /** Returns true when the session accepted the inject (user-turn gate open). */
    @Volatile
    var sendSpokenUserCommand: ((String) -> Boolean)? = null

    @Volatile
    var disconnectCommand: (() -> Unit)? = null

    @Volatile
    var pulseMicLevelForSpeech: ((String) -> Unit)? = null

    @Volatile
    var beginTestUserSpeech: (() -> Unit)? = null

    @Volatile
    var endTestUserSpeech: (() -> Unit)? = null

    /**
     * When true, [GrokVoiceSession] never opens the mic. E2E drives turns only via injects.
     * Set before connect; cleared on disconnect.
     */
    @Volatile
    var skipLiveCapture: Boolean = false
        private set

    fun enableE2eInjectOnlyMode() {
        skipLiveCapture = true
        VoiceLog.i("Debug", "E2E inject-only mode ON (no live mic / no user-audio cue)")
    }

    fun clearE2eMode() {
        skipLiveCapture = false
        VoiceLog.d("Debug", "E2E inject-only mode cleared")
    }

    fun dispatch(text: String): Boolean {
        val handler = sendTextCommand ?: return false
        handler(text)
        return true
    }

    fun dispatchSpoken(text: String): Boolean {
        val handler = sendSpokenUserCommand ?: return false
        return handler(text)
    }

    fun disconnect(): Boolean {
        val handler = disconnectCommand ?: return false
        handler()
        return true
    }

    fun pulseMicLevel(text: String): Boolean {
        if (skipLiveCapture) return true
        val handler = pulseMicLevelForSpeech ?: return false
        handler(text)
        return true
    }

    fun beginTestSpeech(): Boolean {
        if (skipLiveCapture) return true
        val handler = beginTestUserSpeech ?: return false
        handler()
        return true
    }

    fun endTestSpeech(): Boolean {
        if (skipLiveCapture) return true
        val handler = endTestUserSpeech ?: return false
        handler()
        return true
    }
}
