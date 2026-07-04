package com.example.roborazzidemo.voice

/**
 * Debug-only hook so emulator/CI E2E can drive the voice session with text input
 * when the virtual microphone path is unreliable.
 */
object VoiceDebugBridge {
    @Volatile
    var sendTextCommand: ((String) -> Unit)? = null

    @Volatile
    var disconnectCommand: (() -> Unit)? = null

    fun dispatch(text: String): Boolean {
        val handler = sendTextCommand ?: return false
        handler(text)
        return true
    }

    fun disconnect(): Boolean {
        val handler = disconnectCommand ?: return false
        handler()
        return true
    }
}