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
    private const val PCM_TIMEOUT_MS = 90_000L

    fun warmUp(context: Context) {
        context.sendBroadcast(
            Intent(VoiceDebugReceiver.ACTION_VOICE_TEST_WARMUP).setPackage(context.packageName),
        )
        Thread.sleep(WARMUP_MS)
    }

    fun speak(context: Context, text: String) {
        dispatchOrderedPcm(
            context = context,
            intent = pcmIntent(context, VoiceDebugReceiver.ACTION_VOICE_PCM_SPEAK)
                .putExtra(VoiceDebugReceiver.EXTRA_TEXT, text),
            failureLabel = "PCM speech for \"$text\"",
        )
    }

    fun speakPcmBytes(context: Context, pcm: ByteArray) {
        dispatchOrderedPcm(
            context = context,
            intent = pcmIntent(context, VoiceDebugReceiver.ACTION_VOICE_PCM_BYTES)
                .putExtra(
                    VoiceDebugReceiver.EXTRA_PCM,
                    android.util.Base64.encodeToString(pcm, android.util.Base64.NO_WRAP),
                ),
            failureLabel = "PCM bytes (${pcm.size} bytes)",
        )
    }

    private fun pcmIntent(context: Context, action: String): Intent =
        Intent(action)
            .setPackage(context.packageName)
            .apply {
                if (VoiceE2eConfig.isAudiblePromptsEnabled()) {
                    putExtra(VoiceDebugReceiver.EXTRA_MIRROR_PCM, true)
                }
            }

    private fun dispatchOrderedPcm(context: Context, intent: Intent, failureLabel: String) {
        val latch = CountDownLatch(1)
        var ok = false
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, received: Intent?) {
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
            "Timed out waiting for $failureLabel"
        }
        check(ok) { "Failed streaming $failureLabel" }
    }
}