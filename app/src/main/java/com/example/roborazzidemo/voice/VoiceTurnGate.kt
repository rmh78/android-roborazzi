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
    ): Boolean =
        sessionConfigured &&
            activeResponseId == null &&
            !toolFollowupResponsePending &&
            playbackIdle &&
            micGateState == MicCaptureGate.State.Streaming &&
            captureActive &&
            (!captureMuted || VoiceDeviceHints.useHalfDuplexVoice())
}