package com.example.roborazzidemo.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCaptureGateTest {
    @Test
    fun textInjectFlow_resumesOnlyAfterSpokenResponse() {
        val gate = MicCaptureGate()
        gate.onCaptureStarted()
        assertEquals(MicCaptureGate.State.Streaming, gate.state)

        gate.pauseForTextInject()
        assertEquals(MicCaptureGate.State.PausedForTextInject, gate.state)
        assertFalse(gate.shouldResumeCaptureAfterResponse(hadAudio = true))

        gate.holdUntilSpokenDone()
        assertEquals(MicCaptureGate.State.HeldUntilSpokenDone, gate.state)
        assertFalse(gate.shouldResumeCaptureAfterResponse(hadAudio = false))
        assertTrue(gate.shouldResumeCaptureAfterResponse(hadAudio = true))

        gate.onCaptureResumed()
        assertEquals(MicCaptureGate.State.Streaming, gate.state)
        assertFalse(gate.shouldResumeCaptureAfterResponse(hadAudio = true))
    }
}