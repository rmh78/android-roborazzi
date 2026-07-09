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
 * E2E user turns: **text inject only** by default (no TTS, no beep, no live mic).
 *
 * That keeps AVD CPU stable — the previous cue audio + AudioRecord path was the spike
 * on every user step. Optional cues remain available via instrumentation args.
 */
object TestSpeechAnnouncer {
    private const val INJECT_ATTEMPTS = 12
    private const val INJECT_RETRY_MS = 400L
    private const val INJECT_RESULT_TIMEOUT_MS = 5_000L
    private const val CUE_TIMEOUT_MS = 45_000L

    private enum class CueMode { None, Beep, Tts }

    /**
     * Enable inject-only session mode (skip AudioRecord) before connect.
     * Safe to call multiple times.
     */
    fun enableInjectOnlySession(context: Context) {
        val latch = CountDownLatch(1)
        var ok = false
        context.sendOrderedBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_E2E_MODE)
                .setPackage(context.packageName)
                .putExtra(VoiceDebugReceiver.EXTRA_INJECT_ONLY, true)
                .putExtra(VoiceDebugReceiver.EXTRA_SKIP_LIVE_CAPTURE, true),
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
        check(latch.await(3_000L, TimeUnit.MILLISECONDS)) {
            "Timed out enabling E2E inject-only mode"
        }
        check(ok) { "Failed to enable E2E inject-only mode" }
        VoiceE2ELog.step("E2E inject-only mode enabled (no live mic / no user audio cue)")
    }

    fun warmUp(context: Context) {
        // Inject-only: nothing to warm. Optional TTS still pre-inits when requested.
        if (cueMode() == CueMode.Tts) {
            context.sendBroadcast(
                Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP)
                    .setPackage(context.packageName)
                    .putExtra(VoiceDebugReceiver.EXTRA_SPEECH_MODE, "tts"),
            )
            Thread.sleep(2_000L)
        }
    }

    fun speak(context: Context, text: String) {
        val cue = cueMode()
        if (cue != CueMode.None) {
            context.sendBroadcast(
                Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_SPEECH_BEGIN)
                    .setPackage(context.packageName),
            )
            try {
                playCue(context, text, cue)
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

    /**
     * Default: no audible user cue (inject only).
     * Opt-in: `testSpeechMode=beep` or `tts`.
     */
    private fun cueMode(): CueMode {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("disableTestSpeechPlayback") == "true") return CueMode.None
        return when (args.getString("testSpeechMode")?.lowercase()) {
            "beep" -> CueMode.Beep
            "tts", "speech" -> CueMode.Tts
            "none", "off", "inject", null, "" -> CueMode.None
            else -> CueMode.None
        }
    }

    private fun playCue(context: Context, text: String, mode: CueMode) {
        val modeExtra = when (mode) {
            CueMode.Tts -> "tts"
            CueMode.Beep -> "beep"
            CueMode.None -> return
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
