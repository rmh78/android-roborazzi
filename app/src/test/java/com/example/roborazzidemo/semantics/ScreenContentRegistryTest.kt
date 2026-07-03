package com.example.roborazzidemo.semantics

import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContentRegistryTest {
    @Test
    fun toJson_includesScreenAndElements() {
        ScreenContentRegistry.update(
            route = "home",
            screenElements = listOf(
                ScreenElement("button", "Browse Items"),
            ),
        )

        val json = ScreenContentRegistry.toJson()

        assertTrue(json.contains("\"screen\":\"home\""))
        assertTrue(json.contains("Browse Items"))
    }
}