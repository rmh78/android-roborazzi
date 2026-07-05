package com.example.roborazzidemo.ci

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.roborazzidemo.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * No-op connected test that compiles/installs the app and APKs so CI can persist the Gradle
 * build cache after a successful workflow run. Keeps round-trips fast for [com.example.roborazzidemo.voice.VoiceAppIntegrationTest].
 */
@RunWith(AndroidJUnit4::class)
class GradleCacheWarmupTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun app_launches_home_screen() {
        activityRule.scenario.onActivity { }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle(5_000)
        checkNotNull(device.wait(Until.findObject(By.text("Roborazzi Demo")), 60_000)) {
            "Home screen did not appear during Gradle cache warmup"
        }
    }
}