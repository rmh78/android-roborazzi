package com.example.roborazzidemo.voice.support

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roborazzidemo.voice.VoiceDebugReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator TTS playback + [VoiceDebugReceiver.ACTION_VOICE_SPOKEN] inject for E2E tests. */
object TestSpeechAnnouncer {
    private const val WARMUP_MS = 2_000L
    private const val TTS_TIMEOUT_MS = 45_000L

    fun warmUp(context: Context) {
        if (!speechEnabled()) return
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP).setPackage(context.packageName),
        )
        Thread.sleep(WARMUP_MS)
    }

    fun speak(context: Context, text: String) {
        if (speechEnabled()) {
            playTts(context, text)
        }
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_SPOKEN)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text),
        )
    }

    private fun speechEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("disableTestSpeechPlayback") != "true"

    private fun playTts(context: Context, text: String) {
        val latch = CountDownLatch(1)
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
        check(latch.await(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting for emulator TTS: \"$text\""
        }
        check(ok) { "Emulator TTS failed for: \"$text\"" }
    }
}