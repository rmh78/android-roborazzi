package com.example.roborazzidemo.voice

/**
 * Tracks mic capture lifecycle during live streaming vs debug text inject.
 */
class MicCaptureGate {
    enum class State {
        Streaming,
        PausedForTextInject,
        HeldUntilSpokenDone,
    }

    var state: State = State.Streaming
        private set

    fun onCaptureStarted() {
        state = State.Streaming
    }

    fun pauseForTextInject() {
        state = State.PausedForTextInject
    }

    fun holdUntilSpokenDone() {
        state = State.HeldUntilSpokenDone
    }

    /** [VOICE_TEXT] direct-speech inject: restore tools and mic after a spoken assistant reply. */
    fun shouldRestoreToolsAfterDirectSpeech(hadAudio: Boolean): Boolean =
        state == State.HeldUntilSpokenDone && hadAudio

    /** [VOICE_TEXT] inject: resume live mic after the turn completes. */
    fun shouldResumeMicAfterTextInject(): Boolean = state == State.PausedForTextInject

    fun onCaptureResumed() {
        state = State.Streaming
    }
}