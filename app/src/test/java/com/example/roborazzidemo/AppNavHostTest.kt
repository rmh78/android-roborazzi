package com.example.roborazzidemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.roborazzidemo.ui.VoiceOverlayChrome
import com.example.roborazzidemo.ui.futuristic.FuturisticBackground
import com.example.roborazzidemo.viewmodel.VoiceUiState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import com.example.roborazzidemo.navigation.NavRoutes
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import org.junit.Test

class AppNavHostTest : RoborazziComposeTest() {
    @Test
    fun browseItems_navigatesToListAndDetail() {
        setThemedContent {
            AppNavHost()
        }

        composeTestRule.onNodeWithText("Browse Items").performClick()
        composeTestRule.onNodeWithText("Items").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_list").assertIsDisplayed()
        captureScreenshot(GoldenImages.NAV_BROWSE_ITEMS_LIST)

        composeTestRule.onNodeWithText("Item 1").performClick()
        composeTestRule.onNodeWithText("Short note.").assertIsDisplayed()
        captureScreenshot(GoldenImages.NAV_BROWSE_ITEMS_DETAIL)

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithTag("item_list").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Browse Items").assertIsDisplayed()
    }

    @Test
    fun viewSampleDetail_navigatesToFirstItem() {
        setThemedContent {
            AppNavHost()
        }

        composeTestRule.onNodeWithText("View Sample Detail").performClick()
        composeTestRule.onNodeWithText("Short note.").assertIsDisplayed()
    }

    @Test
    fun invalidItemId_showsNotFoundScreen() {
        lateinit var navController: TestNavHostController

        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            RoborazziDemoTheme {
                Box(Modifier.fillMaxSize()) {
                    FuturisticBackground(Modifier.fillMaxSize())
                    AppNavHost(navController = navController)
                    VoiceOverlayChrome(state = VoiceUiState.RoborazziDisconnected)
                }
            }
        }
        composeTestRule.waitForIdle()

        navController.navigate(NavRoutes.detail(99999))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Item not found").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "No item exists for this id. Use the back button to return.",
        ).assertIsDisplayed()
        captureScreenshot(GoldenImages.NAV_ITEM_NOT_FOUND)
    }
}