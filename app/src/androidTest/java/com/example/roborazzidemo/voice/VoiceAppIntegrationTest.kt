package com.example.roborazzidemo.voice

import android.Manifest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.roborazzidemo.BuildConfig
import com.example.roborazzidemo.MainActivity
import com.example.roborazzidemo.voice.support.VoiceAppTestRobot
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

        app.assertAppVisible()
        app.assertHomeScreenVisible()

        // Connect: greeting + always-on listening
        app.connect()
        app.waitForVoiceReady(timeoutMillis = 120_000)
        app.assertConnectedVoiceChromeVisible()
        app.assertGreetingTurnIfPresent()

        // Home — describe_screen
        app.speakAndWaitForTool("describe the screen for me", "describe_screen")
        app.assertLastToolWas("describe_screen")
        app.assertExchangeTurns()

        // web_search
        app.speakAndWaitForResponse("what is the weather in Munich", timeoutMillis = 120_000)
        app.assertExchangeTurns()

        // Items list — navigate_to_screen(items)
        app.speakAndWaitForTool("navigate me to the list page", "navigate_to_screen")
        app.waitForItemsListScreen()
        app.assertExchangeTurns()

        // Items list — open_list_item
        app.speakAndWaitForTool("scroll to item 10 in the list", "open_list_item")
        app.waitForListItemVisible("Item 10")
        app.waitForListItemSelected(itemIndex = 10)
        app.assertExchangeTurns()

        // Items list — describe_screen
        app.speakAndWaitForTool("describe the screen", "describe_screen")
        app.assertExchangeTurns()

        // Item detail — navigate_to_screen(detail)
        app.speakAndWaitForTool("open the detail page for item 1", "navigate_to_screen")
        app.waitForItemDetailScreen("Item 1")
        app.assertExchangeTurns()

        // Detail — describe_screen
        app.speakAndWaitForTool("what is on this screen", "describe_screen")
        app.assertExchangeTurns()

        // Back to list — navigate_back
        app.speakAndWaitForTool("go back", "navigate_back")
        app.waitForItemsListScreen()
        app.assertExchangeTurns()

        // Not found — navigate_to_screen(detail, invalid id)
        app.speakAndWaitForTool("navigate to the detail page for item 999", "navigate_to_screen")
        app.waitForItemNotFoundScreen()
        app.assertExchangeTurns()

        // Home — navigate_to_screen(home)
        app.speakAndWaitForTool("go to the home screen", "navigate_to_screen")
        app.waitForHomeScreen()
        app.assertExchangeTurns()

        // Full conversation: optional Grok greeting, then alternating You→Grok exchanges
        app.assertConversationTurnCounts(minYou = 10, minGrok = 9)
        app.assertValidConversationTurns()

        // Disconnect
        app.disconnect()
        app.waitUntilDisconnected()
    }
}