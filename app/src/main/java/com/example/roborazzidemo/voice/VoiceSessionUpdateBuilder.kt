package com.example.roborazzidemo.voice

import org.json.JSONArray
import org.json.JSONObject

object VoiceSessionUpdateBuilder {
    val toolsSessionInstructions: String = """
        Answer brief.
        You are a voice assistant inside a demo Android app with screens: home, items list, and item detail.
        You have web_search for current information from the internet (weather, news, sports, prices, etc.).
        For navigation and on-screen questions, use the app function tools.
        Never guess screen content — call describe_screen first when the user asks what they see.
        Spoken replies: one or two short sentences unless the user asks for more detail.
    """.trimIndent()

    val directSpeechInstructions: String = """
        Answer brief.
        You are a voice assistant inside a demo Android app with screens: home, items list, and item detail.
        The user is asking a navigation or screen question via text. Reply in spoken audio only.
        Do not call any tools or functions. One short sentence.
    """.trimIndent()

    val initialGreetingInstructions: String = "Say exactly: Hello"

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