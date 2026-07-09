package com.example.roborazzidemo.voice

/**
 * Session-side gate: user speech (live mic or debug inject) is only allowed when
 * Grok is not responding and assistant PCM playback has fully drained.
 */
internal object VoiceTurnGate {
    fun isUserTurnAllowed(
        sessionConfigured: Boolean,
        activeResponseId: String?,
        toolFollowupResponsePending: Boolean,
        playbackIdle: Boolean,
        micGateState: MicCaptureGate.State,
        captureActive: Boolean,
        captureMuted: Boolean,
        testUserSpeechPlayback: Boolean,
        /** E2E inject-only: no AudioRecord — gate ignores capture hardware state. */
        skipLiveCapture: Boolean = false,
    ): Boolean {
        if (!sessionConfigured) return false
        if (activeResponseId != null) return false
        if (toolFollowupResponsePending) return false
        if (!playbackIdle) return false
        if (testUserSpeechPlayback) return false
        if (micGateState != MicCaptureGate.State.Streaming) return false
        if (skipLiveCapture) return true
        return captureActive && !captureMuted
    }
}
