package com.example.roborazzidemo.voice.support

import com.example.roborazzidemo.voice.VoiceConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Generates short PCM16 test tones for emulator voice setup checks. */
object TestPcmTone {
    fun sineTone(
        durationMs: Long = 500L,
        frequencyHz: Double = 440.0,
        trailingSilenceMs: Long = 600L,
    ): ByteArray {
        val speechSamples = (VoiceConstants.SAMPLE_RATE_HZ * durationMs / 1_000L).toInt()
        val speech = ByteArray(speechSamples * 2)
        val buffer = ByteBuffer.wrap(speech).order(ByteOrder.LITTLE_ENDIAN)
        repeat(speechSamples) { index ->
            val sample = sin(2.0 * Math.PI * frequencyHz * index / VoiceConstants.SAMPLE_RATE_HZ)
            buffer.putShort(
                (sample * 12_000.0).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort(),
            )
        }
        val silence = ByteArray((VoiceConstants.SAMPLE_RATE_HZ * trailingSilenceMs / 1_000L * 2).toInt())
        return speech + silence
    }
}