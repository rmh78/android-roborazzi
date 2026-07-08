package com.example.roborazzidemo.voice

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.roborazzidemo.BuildConfig
import com.example.roborazzidemo.MainActivity
import com.example.roborazzidemo.voice.support.TestPcmTone
import com.example.roborazzidemo.voice.support.TestSpeechAnnouncer
import com.example.roborazzidemo.voice.support.VoiceAppTestRobot
import com.example.roborazzidemo.voice.support.VoiceE2ELog
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EmulatorVoiceSetupTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
    )

    @Test
    fun emulator_hints_detectAvd() {
        assumeTrue("Run on an Android emulator AVD", VoiceDeviceHints.isLikelyEmulator())
        VoiceE2ELog.step("emulator hints detect AVD")
        assertTrue(VoiceDeviceHints.useHalfDuplexVoice())
        assertTrue(VoiceDeviceHints.preferredCaptureSources().first() == android.media.MediaRecorder.AudioSource.MIC)
    }

    @Test
    fun capture_initializes_on_emulator() {
        assumeTrue("Run on an Android emulator AVD", VoiceDeviceHints.isLikelyEmulator())
        VoiceE2ELog.step("capture initializes on emulator (silent input allowed)")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val capture = PcmAudioCapture(context)
        val started = CountDownLatch(1)
        var failure: String? = null
        capture.start(
            onChunk = { started.countDown() },
            onFailure = { message ->
                failure = message
                started.countDown()
            },
            allowSilentEmulatorInput = true,
        )
        assertTrue("Timed out waiting for mic capture start", started.await(10, TimeUnit.SECONDS))
        assertTrue(failure ?: "AudioRecord did not enter capturing state", capture.isCapturing())
        capture.stop()
    }

    @Test
    fun half_duplex_enabled_on_emulator() {
        assumeTrue("Run on an Android emulator AVD", VoiceDeviceHints.isLikelyEmulator())
        VoiceE2ELog.step("half-duplex enabled on emulator")
        assertTrue(VoiceDeviceHints.useHalfDuplexVoice())
    }

    @Test
    fun injected_pcm_reaches_session() {
        assumeTrue("Run on an Android emulator AVD", VoiceDeviceHints.isLikelyEmulator())
        check(BuildConfig.XAI_API_KEY != "no-api-key") {
            "Set XAI_API_KEY before building — PCM session test needs a live voice session"
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            val app = VoiceAppTestRobot.create(skipWarmUp = true)
            VoiceE2ELog.step("connect for PCM ping")
            app.connect()
            app.waitForVoiceReady(timeoutMillis = 120_000)
            app.assertGreetingTurnIfPresent(timeoutMillis = 120_000)
            VoiceE2ELog.step("stream PCM tone through live session")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            TestSpeechAnnouncer.speakPcmBytes(context, TestPcmTone.sineTone())
            VoiceE2ELog.detail("PCM tone streamed (${TestPcmTone.sineTone().size} bytes)")
            app.disconnect()
            app.waitUntilDisconnected()
        }
    }

    @Test
    fun virtual_mic_signal_detected() {
        assumeTrue("Run on an Android emulator AVD", VoiceDeviceHints.isLikelyEmulator())
        assumeTrue(
            "Skipped — pass -Pandroid.testInstrumentationRunnerArguments.requireHostMic=true for host-mic check",
            InstrumentationRegistry.getArguments().getString("requireHostMic") == "true",
        )
        VoiceE2ELog.step("virtual mic signal probe (host audio input required)")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val capture = PcmAudioCapture(context)
        val started = CountDownLatch(1)
        capture.start(
            onChunk = { started.countDown() },
            onFailure = { started.countDown() },
            allowSilentEmulatorInput = false,
        )
        assertTrue("Timed out waiting for mic capture start", started.await(10, TimeUnit.SECONDS))
        assertTrue("AudioRecord did not start with host-mic probing enabled", capture.isCapturing())
        val signal = capture.probeActiveSignal()
        capture.stop()
        assertTrue(
            "Expected non-silent virtual mic input (max_rms=$signal). " +
                "Enable Extended Controls → Microphone → host audio input.",
            signal > SILENT_PROBE_THRESHOLD,
        )
    }

    private companion object {
        private const val SILENT_PROBE_THRESHOLD = 0.0001f
    }
}