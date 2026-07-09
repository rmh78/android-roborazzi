package com.example.roborazzidemo.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class VoiceCatalogClientTest {
    @Test
    fun parseVoicesResponse_sortsByNameAndMapsFields() {
        val json = """
            {
              "voices": [
                {"voice_id": "zagan", "name": "Zagan", "gender": "male"},
                {"voice_id": "eve", "name": "Eve", "gender": "female"},
                {"voice_id": "luna", "name": "Luna", "gender": "female"}
              ]
            }
        """.trimIndent()

        val voices = VoiceCatalogClient.parseVoicesResponse(json)

        assertEquals(listOf("eve", "luna", "zagan"), voices.map { it.id })
        assertEquals("Eve", voices.first { it.id == "eve" }.name)
        assertEquals("female", voices.first { it.id == "eve" }.gender)
    }

    @Test
    fun parseVoicesResponse_skipsInvalidEntries() {
        val json = """{"voices":[{"voice_id":"","name":"Blank"},{"name":"NoId"}]}"""

        assertTrue(VoiceCatalogClient.parseVoicesResponse(json).isEmpty())
    }
}