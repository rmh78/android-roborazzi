package com.example.roborazzidemo

import com.example.roborazzidemo.ui.HomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = RobolectricDeviceQualifiers.SmallPhone, sdk = [33])
class HomeScreenSmallPhoneTest : RoborazziComposeTest() {
    @Test
    fun homeScreen_small_phone_day() {
        setThemedContent(darkTheme = false) {
            HomeScreen(
                onBrowseItems = {},
                onViewSampleDetail = {},
            )
        }
        captureScreenshot(GoldenImages.homeScreenResolution("small_phone", "day"))
    }
}