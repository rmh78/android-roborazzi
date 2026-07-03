package com.example.roborazzidemo

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

        captureScreenshot(GoldenImages.HOME_DARK)
    }
}