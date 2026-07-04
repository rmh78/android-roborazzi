package com.example.roborazzidemo.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceSessionUpdateBuilderTest {
    @Test
    fun withTools_includesToolDefinitions() {
        val json = VoiceSessionUpdateBuilder.withTools()
        val session = json.getJSONObject("session")
        val tools = session.getJSONArray("tools")
        assertTrue(tools.length() > 0)
        assertEquals(VoiceSessionUpdateBuilder.toolsSessionInstructions, session.getString("instructions"))
    }

    @Test
    fun directSpeechForDebugInject_hasNoToolsAndDifferentInstructions() {
        val json = VoiceSessionUpdateBuilder.directSpeechForDebugInject()
        val session = json.getJSONObject("session")
        val tools = session.getJSONArray("tools")
        assertEquals(0, tools.length())
        assertEquals(VoiceSessionUpdateBuilder.directSpeechInstructions, session.getString("instructions"))
        assertNotEquals(
            VoiceSessionUpdateBuilder.toolsSessionInstructions,
            session.getString("instructions"),
        )
    }
}