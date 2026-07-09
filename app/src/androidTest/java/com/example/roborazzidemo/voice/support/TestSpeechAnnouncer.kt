package com.example.roborazzidemo.voice.support

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roborazzidemo.voice.VoiceDebugReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays audible user TTS, then injects the turn via [VoiceDebugReceiver.ACTION_VOICE_SPOKEN].
 * The robot waits for listening-ready status before calling [speak].
 *
 * Order: mute mic → TTS (user hears the prompt) → unmute → inject (transcript + server turn).
 * Inject is retried while the user-turn gate is still closed (e.g. brief post-Hello mic spin-up).
 */
object TestSpeechAnnouncer {
    private const val WARMUP_MS = 2_000L
    private const val TTS_TIMEOUT_MS = 45_000L
    private const val INJECT_ATTEMPTS = 12
    private const val INJECT_RETRY_MS = 500L
    private const val INJECT_RESULT_TIMEOUT_MS = 5_000L

    fun warmUp(context: Context) {
        if (!speechEnabled()) return
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP).setPackage(context.packageName),
        )
        Thread.sleep(WARMUP_MS)
    }

    fun speak(context: Context, text: String) {
        if (speechEnabled()) {
            context.sendBroadcast(
                Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_SPEECH_BEGIN)
                    .setPackage(context.packageName),
            )
            try {
                playTts(context, text)
            } finally {
                context.sendBroadcast(
                    Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_SPEECH_END)
                        .setPackage(context.packageName),
                )
            }
        }
        injectSpokenWithRetry(context, text)
    }

    private fun injectSpokenWithRetry(context: Context, text: String) {
        repeat(INJECT_ATTEMPTS) { attempt ->
            if (dispatchSpoken(context, text)) {
                if (attempt > 0) {
                    VoiceE2ELog.detail("VOICE_SPOKEN accepted on retry ${attempt + 1}: \"$text\"")
                }
                return
            }
            VoiceE2ELog.detail(
                "VOICE_SPOKEN gate closed (attempt ${attempt + 1}/$INJECT_ATTEMPTS) — \"$text\"",
            )
            Thread.sleep(INJECT_RETRY_MS)
        }
        error(
            "VOICE_SPOKEN inject rejected after $INJECT_ATTEMPTS attempts " +
                "(user-turn gate closed or session missing): \"$text\"",
        )
    }

    private fun dispatchSpoken(context: Context, text: String): Boolean {
        val latch = CountDownLatch(1)
        var accepted = false
        context.sendOrderedBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_SPOKEN)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    accepted = resultCode == Activity.RESULT_OK
                    latch.countDown()
                }
            },
            null,
            Activity.RESULT_CANCELED,
            null,
            null,
        )
        check(latch.await(INJECT_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting for VOICE_SPOKEN result: \"$text\""
        }
        return accepted
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
