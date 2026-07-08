package com.example.roborazzidemo.voice.support

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.roborazzidemo.voice.VoiceDebugReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Streams synthesized user speech as PCM via [VoiceDebugReceiver.ACTION_VOICE_PCM_SPEAK].
 * The robot waits for [voice-turn-phase-listening] before calling [speak].
 */
object TestSpeechAnnouncer {
    private const val WARMUP_MS = 2_000L
    private const val PCM_TIMEOUT_MS = 60_000L

    fun warmUp(context: Context) {
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP).setPackage(context.packageName),
        )
        Thread.sleep(WARMUP_MS)
    }

    fun speak(context: Context, text: String) {
        val latch = CountDownLatch(1)
        var ok = false
        context.sendOrderedBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_PCM_SPEAK)
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
        check(latch.await(PCM_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Timed out waiting for PCM speech synthesis: \"$text\""
        }
        check(ok) { "PCM speech synthesis failed for: \"$text\"" }
    }

    fun speakPcmBytes(context: Context, pcm: ByteArray) {
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_PCM_BYTES)
                .setPackage(context.packageName)
                .putExtra(
                    VoiceDebugReceiver.EXTRA_PCM,
                    android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP),
                ),
        )
    }
}