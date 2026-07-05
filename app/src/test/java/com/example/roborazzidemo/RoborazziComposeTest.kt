package com.example.roborazzidemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.example.roborazzidemo.ui.futuristic.FuturisticBackground
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roborazzidemo.theme.RoborazziDemoTheme
import com.github.takahirom.roborazzi.AwtImageLoader
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.JvmImageIoFormat
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.awt.image.BufferedImage
import java.io.File
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
            imageIoFormat = losslessWebPImageIoFormat(),
        ),
    )

    protected fun setThemedContent(
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            RoborazziDemoTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    FuturisticBackground(Modifier.fillMaxSize())
                    content()
                }
            }
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

}

private fun losslessWebPImageIoFormat(): JvmImageIoFormat {
    val base = LosslessWebPImageIoFormat() as JvmImageIoFormat
    return base.copy(
        awtImageLoader = AwtImageLoader { file ->
            readImageWithSystemClassLoader(file)
                ?: error("Failed to read image: ${file.absolutePath}")
        },
    )
}

private fun readImageWithSystemClassLoader(file: File): BufferedImage? {
    val imageIoClass = ClassLoader.getSystemClassLoader().loadClass("javax.imageio.ImageIO")
    val readMethod = imageIoClass.getMethod("read", File::class.java)
    return readMethod.invoke(null, file) as? BufferedImage
}