package com.example.roborazzidemo.semantics

import org.json.JSONArray
import org.json.JSONObject

data class ScreenElement(
    val role: String,
    val text: String,
    val description: String? = null,
)

object ScreenContentRegistry {
    private val elements = mutableListOf<ScreenElement>()
    private var screenRoute: String = "unknown"

    fun update(route: String, screenElements: List<ScreenElement>) {
        screenRoute = route
        elements.clear()
        elements.addAll(screenElements)
    }

    fun toJson(): String {
        val array = JSONArray()
        elements.forEach { element ->
            array.put(
                JSONObject().apply {
                    put("role", element.role)
                    put("text", element.text)
                    element.description?.let { put("description", it) }
                },
            )
        }
        return JSONObject().apply {
            put("screen", screenRoute)
            put("elements", array)
        }.toString()
    }
}