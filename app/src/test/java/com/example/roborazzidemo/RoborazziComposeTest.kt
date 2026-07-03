package com.example.roborazzidemo

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, sdk = [33])
@OptIn(ExperimentalRoborazziApi::class)
abstract class RoborazziComposeTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()

    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat(),
        ),
    )

    protected fun setThemedContent(
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            RoborazziDemoTheme(darkTheme = darkTheme, content = content)
        }
        composeTestRule.waitForIdle()
    }

    protected fun captureScreenshot(name: String) {
        val fileName = if (name.endsWith(".webp")) name else "$name.webp"
        composeTestRule.onRoot().captureRoboImage(
            fileName,
            roborazziOptions = roborazziOptions,
        )
    }

    protected fun captureThemedRoboImage(
        name: String,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val fileName = if (name.endsWith(".webp")) name else "$name.webp"
        captureRoboImage(fileName, roborazziOptions = roborazziOptions) {
            RoborazziDemoTheme(darkTheme = darkTheme, content = content)
        }
    }
}