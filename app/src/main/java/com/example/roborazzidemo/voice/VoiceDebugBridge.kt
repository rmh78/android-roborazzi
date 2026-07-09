package com.example.roborazzidemo.voice

/**
 * Debug-only hook so emulator/CI E2E can drive the voice session with text input
 * when the virtual microphone path is unreliable.
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
        val handler = pulseMicLevelForSpeech ?: return false
        handler(text)
        return true
    }

    fun beginTestSpeech(): Boolean {
        val handler = beginTestUserSpeech ?: return false
        handler()
        return true
    }

    fun endTestSpeech(): Boolean {
        val handler = endTestUserSpeech ?: return false
        handler()
        return true
    }
}