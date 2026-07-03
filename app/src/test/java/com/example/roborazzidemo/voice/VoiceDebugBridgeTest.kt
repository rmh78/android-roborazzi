package com.example.roborazzidemo.voice

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDebugBridgeTest {
    @After
    fun tearDown() {
        VoiceDebugBridge.sendTextCommand = null
    }

    @Test
    fun dispatch_returnsFalseWhenNoHandlerRegistered() {
        VoiceDebugBridge.sendTextCommand = null
        assertFalse(VoiceDebugBridge.dispatch("hello"))
    }

    @Test
    fun dispatch_invokesRegisteredHandlerWithText() {
        var received: String? = null
        VoiceDebugBridge.sendTextCommand = { received = it }
        assertTrue(VoiceDebugBridge.dispatch("Go to the items list"))
        assertTrue(received == "Go to the items list")
    }
}