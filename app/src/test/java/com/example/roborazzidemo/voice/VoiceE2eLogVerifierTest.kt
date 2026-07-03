package com.example.roborazzidemo.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceE2eLogVerifierTest {
    @Test
    fun verifyConnectPhase_passesForCompleteHandshake() {
        val log = """
            I VoiceAssistant: [Session] WebSocket open (HTTP 101)
            D VoiceAssistant: [Session] ← conversation.created
            D VoiceAssistant: [Session] → session.update
            D VoiceAssistant: [Session] ← session.updated
            I VoiceAssistant: [Session] Session configured — starting audio capture
            I VoiceAssistant: [Session] Mic streaming active (100ms PCM16 chunks)
        """.trimIndent()

        val result = VoiceE2eLogVerifier.verifyConnectPhase(log)
        assertTrue(result.failures.toString(), result.passed)
    }

    @Test
    fun verifyTextTurnSequence_passesForOrderedNavigationTurn() {
        val log = """
            I VoiceAssistant: [Debug] Forwarding text to session: Go to the items list
            D VoiceAssistant: [Session] → conversation.item.create (text: Go to the items list)
            D VoiceAssistant: [Session] → response.create (after text message)
            I VoiceAssistant: [Debug] Injected text command: Go to the items list
            D VoiceAssistant: [Session] ← response.created | response_id=abc
            D VoiceAssistant: [Session] ← response.function_call_arguments.done | tool=navigate_to_screen
            D VoiceAssistant: [Session] ← response.done | response_id=abc
            D VoiceAssistant: [Session] → conversation.item.create (function_call_output, call_id=x)
            D VoiceAssistant: [Session] → response.create (after tool result)
            D VoiceAssistant: [Session] ← response.created | response_id=def
            D VoiceAssistant: [Session] ← response.audio.delta | bytes=35840
            D VoiceAssistant: [Session] ← response.done | response_id=def
        """.trimIndent()

        val result = VoiceE2eLogVerifier.verifyTextTurnSequence(log)
        assertTrue(result.failures.toString(), result.passed)
    }

    @Test
    fun verifyTextTurnSequence_failsWhenAudioMissingBeforeFinalDone() {
        val log = """
            I VoiceAssistant: [Debug] Forwarding text to session: Go to the items list
            D VoiceAssistant: [Session] → conversation.item.create (text: Go to the items list)
            D VoiceAssistant: [Session] → response.create (after text message)
            I VoiceAssistant: [Debug] Injected text command: Go to the items list
            D VoiceAssistant: [Session] ← response.created | response_id=abc
            D VoiceAssistant: [Session] ← response.done | response_id=abc
        """.trimIndent()

        val result = VoiceE2eLogVerifier.verifyTextTurnSequence(log)
        assertFalse(result.passed)
    }

    @Test
    fun verifyCapturedEmulatorLogs_passAcceptanceCriteria() {
        val connectLog = readResource("voice-connect-captured.log")
        val e2eLog = readResource("voice-e2e-captured.log")

        val connect = VoiceE2eLogVerifier.verifyConnectPhase(connectLog)
        assertTrue(connect.failures.toString(), connect.passed)

        val turn = VoiceE2eLogVerifier.verifyTextTurnSequence(e2eLog)
        assertTrue(turn.failures.toString(), turn.passed)
    }

    private fun readResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing test resource: $name"
        }.bufferedReader().readText()
}