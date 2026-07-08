package com.example.roborazzidemo.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class VoiceCatalogClient(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchVoices(): List<VoiceOption> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(VOICES_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Voice catalog HTTP ${response.code}: ${body.take(200)}")
            }
            parseVoicesResponse(body)
        }
    }

    companion object {
        private const val VOICES_URL = "https://api.x.ai/v1/tts/voices"

        fun parseVoicesResponse(json: String): List<VoiceOption> {
            val voices = JSONObject(json).optJSONArray("voices")
                ?: return emptyList()
            return buildList {
                for (index in 0 until voices.length()) {
                    val voice = voices.optJSONObject(index) ?: continue
                    val id = voice.optString("voice_id").trim()
                    if (id.isEmpty()) continue
                    add(
                        VoiceOption(
                            id = id,
                            name = voice.optString("name", id),
                            gender = voice.optString("gender").ifBlank { null },
                        ),
                    )
                }
            }.sortedBy { it.name.lowercase() }
        }
    }
}