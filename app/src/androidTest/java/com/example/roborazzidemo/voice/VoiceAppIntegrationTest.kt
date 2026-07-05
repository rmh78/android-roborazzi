package com.example.roborazzidemo.voice

import android.Manifest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.roborazzidemo.BuildConfig
import com.example.roborazzidemo.MainActivity
import com.example.roborazzidemo.voice.support.VoiceAppTestRobot
import com.example.roborazzidemo.voice.support.VoiceE2ELog
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceAppIntegrationTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
    )

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var app: VoiceAppTestRobot

    @Before
    fun setUp() {
        activityRule.scenario.onActivity { }
        app = VoiceAppTestRobot.create()
    }

    @Test
    fun app_connects_exercisesAllVoiceToolsAndNavScreens_thenDisconnects() {
        assumeTrue(
            "Set XAI_API_KEY to run the live voice app integration test",
            BuildConfig.XAI_API_KEY != "no-api-key",
        )

        VoiceE2ELog.step("assert app and home screen visible")
        app.assertAppVisible()
        app.assertHomeScreenVisible()

        VoiceE2ELog.step("connect and wait for voice ready")
        app.connect()
        app.waitForVoiceReady(timeoutMillis = 120_000)
        app.assertConnectedVoiceChromeVisible()
        app.assertGreetingTurnIfPresent()

        VoiceE2ELog.step("home — describe_screen")
        app.speakAndWaitForTool("describe the screen for me", "describe_screen")
        app.assertLastToolWas("describe_screen")
        app.assertExchangeTurns()

        VoiceE2ELog.step("web_search — weather in Munich")
        app.speakAndWaitForResponse("what is the weather in Munich", timeoutMillis = 120_000)
        app.assertExchangeTurns()

        VoiceE2ELog.step("navigate to items list")
        app.speakAndWaitForTool("navigate me to the list page", "navigate_to_screen")
        app.waitForItemsListScreen()
        app.assertExchangeTurns()

        VoiceE2ELog.step("open_list_item — scroll to item 10")
        app.speakAndWaitForTool("scroll to item 10 in the list", "open_list_item")
        app.waitForListItemVisible("Item 10")
        app.waitForListItemSelected(itemIndex = 10)
        app.assertExchangeTurns()

        VoiceE2ELog.step("items list — describe_screen")
        app.speakAndWaitForTool("describe the screen", "describe_screen")
        app.assertExchangeTurns()

        VoiceE2ELog.step("navigate to item 1 detail")
        app.speakAndWaitForTool("open the detail page for item 1", "navigate_to_screen")
        app.waitForItemDetailScreen("Item 1")
        app.assertExchangeTurns()

        VoiceE2ELog.step("detail — describe_screen")
        app.speakAndWaitForTool("what is on this screen", "describe_screen")
        app.assertExchangeTurns()

        VoiceE2ELog.step("back to items list via navigate_back")
        app.speakAndWaitForTool("go back", "navigate_back")
        app.waitForItemsListScreen()
        app.assertExchangeTurns()

        VoiceE2ELog.step("navigate to item 999 (not found)")
        app.speakAndWaitForTool("navigate to the detail page for item 999", "navigate_to_screen")
        app.waitForItemNotFoundScreen()
        app.assertExchangeTurns()

        VoiceE2ELog.step("return to items list from not-found via navigate_to_screen")
        app.speakAndWaitForTool("navigate to the items list", "navigate_to_screen")
        app.waitForItemsListScreen()
        app.assertExchangeTurns()

        VoiceE2ELog.step("navigate to home screen")
        app.speakAndWaitForTool("go to the home screen", "navigate_to_screen")
        app.waitForHomeScreen()
        app.assertExchangeTurns()

        VoiceE2ELog.step("assert conversation turn counts and order")
        app.assertConversationTurnCounts(minYou = 11, minGrok = 10)
        app.assertValidConversationTurns()

        VoiceE2ELog.step("disconnect")
        app.disconnect()
        app.waitUntilDisconnected()
    }
}