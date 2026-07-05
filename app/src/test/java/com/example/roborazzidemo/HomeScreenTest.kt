package com.example.roborazzidemo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.roborazzidemo.ui.HomeScreen
import org.junit.Test

class HomeScreenTest : RoborazziComposeTest() {
    @Test
    fun homeScreen_defaultState() {
        setThemedContent {
            HomeScreen(
                onBrowseItems = {},
                onViewSampleDetail = {},
            )
        }

        composeTestRule.onNodeWithText("Roborazzi Demo").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Explore screens and screenshot-test composables with Roborazzi.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Browse Items").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Sample Detail").assertIsDisplayed()
        captureScreenshot(GoldenImages.HOME_DEFAULT)
    }

    @Test
    fun homeScreen_darkTheme() {
        setThemedContent(darkTheme = true) {
            HomeScreen(
                onBrowseItems = {},
                onViewSampleDetail = {},
            )
        }

        composeTestRule.onNodeWithText("Browse Items").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Sample Detail").assertIsDisplayed()
        captureScreenshot(GoldenImages.HOME_DARK)
    }

}