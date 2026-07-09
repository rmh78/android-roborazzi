package com.example.roborazzidemo.voice

/**
 * Debug-only hook so emulator/CI E2E can drive the voice session with text injects.
 *
 * [skipLiveCapture] is only honored under instrumentation. Manual app use always
 * opens the real microphone — E2E mode cannot stick after a test process handoff.
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

    @Volatile
    private var skipLiveCaptureRequested: Boolean = false

    /**
     * True only during instrumented E2E when inject-only was requested.
     * Always false for normal manual launches (even if a prior test left state).
     */
    val skipLiveCapture: Boolean
        get() = skipLiveCaptureRequested && isUnderInstrumentation()

    fun enableE2eInjectOnlyMode() {
        if (!isUnderInstrumentation()) {
            VoiceLog.w("Debug", "Ignoring E2E inject-only outside instrumentation")
            skipLiveCaptureRequested = false
            return
        }
        skipLiveCaptureRequested = true
        VoiceLog.i("Debug", "E2E inject-only mode ON (no live mic / no user-audio cue)")
    }

    fun clearE2eMode() {
        if (skipLiveCaptureRequested) {
            VoiceLog.d("Debug", "E2E inject-only mode cleared")
        }
        skipLiveCaptureRequested = false
    }

    /** Call at the start of every manual/session connect path. */
    fun ensureManualMicUnlessInstrumented() {
        if (!isUnderInstrumentation() && skipLiveCaptureRequested) {
            VoiceLog.w("Debug", "Clearing sticky E2E inject-only flag for manual session")
            skipLiveCaptureRequested = false
        }
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

    /**
     * True when this process is running under an Android instrumentation runner
     * (connectedAndroidTest). Normal app launches always return false.
     */
    fun isUnderInstrumentation(): Boolean =
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val current = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val instrumentation = activityThreadClass.getMethod("getInstrumentation").invoke(current)
            instrumentation != null
        } catch (_: Throwable) {
            false
        }
}
