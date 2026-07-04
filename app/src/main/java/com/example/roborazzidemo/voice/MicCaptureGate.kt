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

    fun shouldResumeCaptureAfterResponse(hadAudio: Boolean): Boolean =
        state == State.HeldUntilSpokenDone && hadAudio

    fun onCaptureResumed() {
        state = State.Streaming
    }

    fun onCaptureStopped() {
        if (state == State.Streaming) {
            state = State.PausedForTextInject
        }
    }
}