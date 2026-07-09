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
 * Audible user-turn cue, then inject via [VoiceDebugReceiver.ACTION_VOICE_SPOKEN].
 *
 * Default cue is a short system **beep** (low CPU). Full TTS of the prompt is opt-in only
 * (`testSpeechMode=tts`) because emulator TextToSpeech spikes CPU and often sounds harsh.
 *
 * Order: mute mic → cue → unmute → inject (retry while gate closed).
 */
object TestSpeechAnnouncer {
    private const val WARMUP_MS = 500L
    private const val CUE_TIMEOUT_MS = 45_000L
    private const val INJECT_ATTEMPTS = 12
    private const val INJECT_RETRY_MS = 500L
    private const val INJECT_RESULT_TIMEOUT_MS = 5_000L

    private enum class Mode { None, Beep, Tts }

    fun warmUp(context: Context) {
        when (speechMode()) {
            Mode.None -> Unit
            Mode.Beep -> {
                // No TTS engine warm-up needed for ToneGenerator.
                context.sendBroadcast(
                    Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP)
                        .setPackage(context.packageName)
                        .putExtra(VoiceDebugReceiver.EXTRA_SPEECH_MODE, "beep"),
                )
                Thread.sleep(WARMUP_MS)
            }
            Mode.Tts -> {
                context.sendBroadcast(
                    Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP)
                        .setPackage(context.packageName)
                        .putExtra(VoiceDebugReceiver.EXTRA_SPEECH_MODE, "tts"),
                )
                Thread.sleep(2_000L)
            }
        }
    }

    fun speak(context: Context, text: String) {
        val mode = speechMode()
        if (mode != Mode.None) {
            context.sendBroadcast(
                Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_SPEECH_BEGIN)
                    .setPackage(context.packageName),
            )
            try {
                playCue(context, text, mode)
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

    private fun speechMode(): Mode {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("disableTestSpeechPlayback") == "true") return Mode.None
        return when (args.getString("testSpeechMode")?.lowercase()) {
            "none", "off", "inject" -> Mode.None
            "tts", "speech" -> Mode.Tts
            "beep", null, "" -> Mode.Beep
            else -> Mode.Beep
        }
    }

    private fun playCue(context: Context, text: String, mode: Mode) {
        val modeExtra = when (mode) {
            Mode.Tts -> "tts"
            Mode.Beep -> "beep"
            Mode.None -> return
        }
        val latch = CountDownLatch(1)
        var ok = false
        context.sendOrderedBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_ANNOUNCE)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text)
                .putExtra(VoiceDebugReceiver.EXTRA_SPEECH_MODE, modeExtra),
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
        check(latch.await(CUE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting for user-turn cue: \"$text\""
        }
        check(ok) { "User-turn cue failed for: \"$text\"" }
    }
}
