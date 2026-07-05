package com.example.roborazzidemo.voice

import org.json.JSONArray
import org.json.JSONObject

object VoiceToolDefinitions {
    fun toolsJson(): JSONArray = JSONArray().apply {
        put(JSONObject().put("type", "web_search"))
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
}