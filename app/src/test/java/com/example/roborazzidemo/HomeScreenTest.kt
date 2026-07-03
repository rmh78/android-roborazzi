package com.example.roborazzidemo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.roborazzidemo.ui.HomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Test
import org.robolectric.RuntimeEnvironment

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

    @Test
    fun homeScreen_screenResolutions() {
        homeScreenResolutions.forEach { resolution ->
            homeScreenThemes.forEach { (theme, darkTheme) ->
                RuntimeEnvironment.setQualifiers(resolution.qualifier)
                captureThemedRoboImage(
                    name = GoldenImages.homeScreenResolution(resolution.slug, theme),
                    darkTheme = darkTheme,
                ) {
                    HomeScreen(
                        onBrowseItems = {},
                        onViewSampleDetail = {},
                    )
                }
            }
        }
    }

    private data class ScreenResolution(
        val slug: String,
        val qualifier: String,
    )

    private companion object {
        val homeScreenResolutions = listOf(
            ScreenResolution("pixel5", RobolectricDeviceQualifiers.Pixel5),
            ScreenResolution("small_phone", RobolectricDeviceQualifiers.SmallPhone),
            ScreenResolution("medium_tablet", RobolectricDeviceQualifiers.MediumTablet),
        )

        val homeScreenThemes = listOf(
            "day" to false,
            "night" to true,
        )
    }
}