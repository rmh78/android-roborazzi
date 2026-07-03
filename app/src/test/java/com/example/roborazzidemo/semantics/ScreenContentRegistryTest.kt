package com.example.roborazzidemo.semantics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
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