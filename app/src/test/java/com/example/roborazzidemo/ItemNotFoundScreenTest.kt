package com.example.roborazzidemo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.roborazzidemo.ui.ItemNotFoundScreen
import org.junit.Test

class ItemNotFoundScreenTest : RoborazziComposeTest() {
    @Test
    fun itemNotFoundScreen_displaysMessage() {
        setThemedContent {
            ItemNotFoundScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Item not found").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "No item exists for this id. Use the back button to return.",
        ).assertIsDisplayed()
        captureScreenshot(GoldenImages.ITEM_NOT_FOUND)
    }
}