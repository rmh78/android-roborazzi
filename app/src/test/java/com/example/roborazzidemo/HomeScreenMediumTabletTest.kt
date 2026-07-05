package com.example.roborazzidemo

import com.example.roborazzidemo.ui.HomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet, sdk = [33])
class HomeScreenMediumTabletTest : RoborazziComposeTest() {
    @Test
    fun homeScreen_medium_tablet_day() = captureHomeScreen("day", darkTheme = false)

    @Test
    fun homeScreen_medium_tablet_night() = captureHomeScreen("night", darkTheme = true)

    private fun captureHomeScreen(theme: String, darkTheme: Boolean) {
        setThemedContent(darkTheme = darkTheme) {
            HomeScreen(
                onBrowseItems = {},
                onViewSampleDetail = {},
            )
        }
        captureScreenshot(GoldenImages.homeScreenResolution("medium_tablet", theme))
    }
}