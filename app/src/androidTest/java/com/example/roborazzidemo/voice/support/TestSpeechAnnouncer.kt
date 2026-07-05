package com.example.roborazzidemo.voice.support

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.roborazzidemo.voice.VoiceDebugReceiver

/** Emulator TTS playback + [VoiceDebugReceiver.ACTION_VOICE_SPOKEN] inject for E2E tests. */
object TestSpeechAnnouncer {
    private const val WARMUP_MS = 2_000L
    private const val TTS_TIMEOUT_MS = 45_000L
    private const val USER_TURN_GATE_TIMEOUT_MS = 120_000L
    private const val USER_TURN_GATE_POLL_MS = 500L
    private const val USER_TURN_STABLE_POLLS = 3

    fun warmUp(context: Context) {
        if (!speechEnabled()) return
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP).setPackage(context.packageName),
        )
        Thread.sleep(WARMUP_MS)
    }

    fun speak(context: Context, text: String) {
        waitForUserTurnAllowed()
        // Inject before TTS — emulator mic picks up speaker audio and server VAD will
        // commit garbage audio if TTS runs while Grok is still speaking.
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_SPOKEN)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text),
        )
        if (speechEnabled()) {
            playTts(context, text)
        }
    }

    private fun waitForUserTurnAllowed() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val deadline = System.currentTimeMillis() + USER_TURN_GATE_TIMEOUT_MS
        var stablePolls = 0
        while (System.currentTimeMillis() < deadline) {
            if (isUserTurnAllowed(device)) {
                stablePolls++
                if (stablePolls >= USER_TURN_STABLE_POLLS) return
            } else {
                stablePolls = 0
            }
            Thread.sleep(USER_TURN_GATE_POLL_MS)
        }
        error(
            "Timed out waiting for voice-user-turn-allowed before spoke command. " +
                "statusMarkers=[${
                    device.findObjects(By.descContains("voice-status-"))
                        .mapNotNull { it.contentDescription }
                        .joinToString()
                }]",
        )
    }

    private fun isUserTurnAllowed(device: UiDevice): Boolean =
        device.findObject(By.desc("voice-user-turn-allowed")) != null &&
            device.findObject(By.desc("voice-assistant-playback-idle")) != null

    private fun speechEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("disableTestSpeechPlayback") != "true"

    private fun playTts(context: Context, text: String) {
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        context.sendOrderedBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_ANNOUNCE)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    ok = resultCode == Activity.RESULT_OK
                    latch.countDown()
                }
            },
            null,
            Activity.RESULT_CANCELED,
            null,
            null,
        )
        check(latch.await(TTS_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            "Timed out waiting for emulator TTS: \"$text\""
        }
        check(ok) { "Emulator TTS failed for: \"$text\"" }
    }
}