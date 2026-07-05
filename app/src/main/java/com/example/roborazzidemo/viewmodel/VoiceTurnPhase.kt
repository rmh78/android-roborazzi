package com.example.roborazzidemo.viewmodel

/** Strict half-duplex turn state exposed to UI and E2E sync. */
enum class VoiceTurnPhase {
    /** Grok audio streaming or assistant turn active. */
    Assistant,
    /** User speaking or prompt being committed. */
    User,
    /** Grok finished — safe to accept the next user prompt. */
    Listening,
}