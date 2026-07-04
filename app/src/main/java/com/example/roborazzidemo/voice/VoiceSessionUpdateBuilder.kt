package com.example.roborazzidemo.voice

import org.json.JSONArray
import org.json.JSONObject

object VoiceSessionUpdateBuilder {
    val toolsSessionInstructions: String = """
        You are a voice assistant inside a demo Android app with screens: home, items list, and item detail.
        Use tools for every navigation action and every question about what is on screen.
        Never guess screen content — call describe_screen first when the user asks what they see.
        Keep spoken answers short and direct.
    """.trimIndent()

    val directSpeechInstructions: String = """
        You are a voice assistant inside a demo Android app with screens: home, items list, and item detail.
        The user is asking a navigation or screen question via text. Answer briefly in spoken audio only.
        Do not call any tools or functions. Describe what you would do or what is on screen in one short sentence.
    """.trimIndent()

    fun withTools(): JSONObject = sessionUpdateJson(
        instructions = toolsSessionInstructions,
        tools = VoiceToolDefinitions.toolsJson(),
    )

    fun directSpeechForDebugInject(): JSONObject = sessionUpdateJson(
        instructions = directSpeechInstructions,
        tools = JSONArray(),
    )

    private fun sessionUpdateJson(instructions: String, tools: JSONArray): JSONObject =
        JSONObject().apply {
            put("type", "session.update")
            put(
                "session",
                JSONObject().apply {
                    put("voice", VoiceConstants.VOICE_ID)
                    put("instructions", instructions)
                    put("reasoning", JSONObject().put("effort", "none"))
                    put("turn_detection", JSONObject().put("type", "server_vad"))
                    put(
                        "audio",
                        JSONObject().apply {
                            put(
                                "input",
                                JSONObject().apply {
                                    put(
                                        "format",
                                        JSONObject().apply {
                                            put("type", "audio/pcm")
                                            put("rate", VoiceConstants.SAMPLE_RATE_HZ)
                                        },
                                    )
                                    put(
                                        "transcription",
                                        JSONObject().apply {
                                            put("model", "grok-transcribe")
                                            put("language_hint", "en")
                                        },
                                    )
                                },
                            )
                            put(
                                "output",
                                JSONObject().apply {
                                    put(
                                        "format",
                                        JSONObject().apply {
                                            put("type", "audio/pcm")
                                            put("rate", VoiceConstants.SAMPLE_RATE_HZ)
                                        },
                                    )
                                },
                            )
                        },
                    )
                    put("tools", tools)
                },
            )
        }
}