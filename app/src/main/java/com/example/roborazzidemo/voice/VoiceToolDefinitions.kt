package com.example.roborazzidemo.voice

import org.json.JSONArray
import org.json.JSONObject

object VoiceToolDefinitions {
    val sessionInstructions: String = """
        You are a voice assistant inside a demo Android app with screens: home, items list, and item detail.
        Use tools for every navigation action and every question about what is on screen.
        Never guess screen content — call describe_screen first when the user asks what they see.
        Keep spoken answers short and direct.
    """.trimIndent()

    fun toolsJson(): JSONArray = JSONArray().apply {
        put(
            JSONObject().apply {
                put("type", "function")
                put("name", "navigate_to_screen")
                put("description", "Navigate to a screen in the app.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "destination",
                                    JSONObject().apply {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            JSONArray().apply {
                                                put("home")
                                                put("items")
                                                put("detail")
                                            },
                                        )
                                        put("description", "Target screen")
                                    },
                                )
                                put(
                                    "item_id",
                                    JSONObject().apply {
                                        put("type", "integer")
                                        put("description", "Required when destination is detail")
                                    },
                                )
                            },
                        )
                        put("required", JSONArray().put("destination"))
                    },
                )
            },
        )
        put(
            JSONObject().apply {
                put("type", "function")
                put("name", "navigate_back")
                put("description", "Go back to the previous screen.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    },
                )
            },
        )
        put(
            JSONObject().apply {
                put("type", "function")
                put("name", "open_list_item")
                put("description", "Scroll the items list to a 1-based item index and highlight it.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "object")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "index",
                                    JSONObject().apply {
                                        put("type", "integer")
                                        put("description", "1-based item index in the list")
                                    },
                                )
                            },
                        )
                        put("required", JSONArray().put("index"))
                    },
                )
            },
        )
        put(
            JSONObject().apply {
                put("type", "function")
                put("name", "describe_screen")
                put("description", "Read the current screen UI tree and return structured content.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    },
                )
            },
        )
    }

    fun sessionUpdateJson(): JSONObject = JSONObject().apply {
        put("type", "session.update")
        put(
            "session",
            JSONObject().apply {
                put("voice", VoiceConstants.VOICE_ID)
                put("instructions", sessionInstructions)
                put("reasoning", JSONObject().put("effort", "none"))
                put(
                    "turn_detection",
                    JSONObject().apply {
                        put("type", "server_vad")
                        put("threshold", 0.65)
                        put("silence_duration_ms", 700)
                    },
                )
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
                put("tools", toolsJson())
            },
        )
    }
}