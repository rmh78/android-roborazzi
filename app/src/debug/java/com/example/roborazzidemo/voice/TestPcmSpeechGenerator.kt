package com.example.roborazzidemo.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

/**
 * Synthesizes E2E prompts to PCM16 @ 24 kHz mono for [GrokVoiceSession.sendPcmUtterance].
 */
object TestPcmSpeechGenerator {
    private const val SYNTH_TIMEOUT_MS = 45_000L
    private const val TRAILING_SILENCE_MS = 1_200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<PendingGenerate>()

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var initFailed = false

    @Volatile
    private var initializing = false

    private val initLock = Any()

    private data class PendingGenerate(
        val text: String,
        val onComplete: (ByteArray?) -> Unit,
    )

    fun warmUp(context: Context) {
        mainHandler.post { ensureEngineOnMainThread(context.applicationContext) }
    }

    fun generate(context: Context, text: String, onComplete: (ByteArray?) -> Unit) {
        mainHandler.post {
            when {
                ready -> generateNow(context.applicationContext, text, onComplete)
                initFailed -> {
                    VoiceLog.w("TestPcm", "TTS unavailable — cannot synthesize PCM for: $text")
                    onComplete(null)
                }
                else -> {
                    pending += PendingGenerate(text, onComplete)
                    ensureEngineOnMainThread(context.applicationContext)
                }
            }
        }
    }

    fun generateTone(
        durationMs: Long = 400L,
        frequencyHz: Double = 440.0,
        trailingSilenceMs: Long = TRAILING_SILENCE_MS,
    ): ByteArray {
        val speechSamples = (VoiceConstants.SAMPLE_RATE_HZ * durationMs / 1_000L).toInt()
        val speech = ByteArray(speechSamples * 2)
        val buffer = ByteBuffer.wrap(speech).order(ByteOrder.LITTLE_ENDIAN)
        repeat(speechSamples) { index ->
            val sample = sin(2.0 * Math.PI * frequencyHz * index / VoiceConstants.SAMPLE_RATE_HZ)
            buffer.putShort((sample * 12_000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        return speech + silencePcm(trailingSilenceMs)
    }

    private fun generateNow(context: Context, text: String, onComplete: (ByteArray?) -> Unit) {
        val engine = tts
        if (engine == null) {
            onComplete(null)
            return
        }
        val output = File(context.cacheDir, "voice-test-pcm-${System.nanoTime()}.wav")
        val finished = AtomicBoolean(false)
        val utteranceId = "voice-pcm-${System.nanoTime()}"

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                private fun complete(success: Boolean) {
                    if (!finished.compareAndSet(false, true)) return
                    val pcm = if (success && output.exists()) {
                        try {
                            decodeWavToPcm24k(output)
                        } catch (e: Exception) {
                            VoiceLog.w("TestPcm", "Failed to decode synthesized WAV: ${e.message}")
                            null
                        } finally {
                            output.delete()
                        }
                    } else {
                        output.delete()
                        null
                    }
                    onComplete(pcm?.let { it + silencePcm(TRAILING_SILENCE_MS) })
                }

                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    complete(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    complete(false)
                }
            },
        )

        VoiceLog.i("TestPcm", "Synthesizing PCM for: \"$text\"")
        val params = Bundle()
        val queued = engine.synthesizeToFile(text, params, output, utteranceId)
        if (queued == TextToSpeech.ERROR) {
            VoiceLog.w("TestPcm", "synthesizeToFile returned ERROR for: \"$text\"")
            output.delete()
            onComplete(null)
            return
        }

        mainHandler.postDelayed(
            {
                if (finished.compareAndSet(false, true)) {
                    VoiceLog.w("TestPcm", "Timed out synthesizing PCM for: \"$text\"")
                    output.delete()
                    onComplete(null)
                }
            },
            SYNTH_TIMEOUT_MS,
        )
    }

    private fun ensureEngineOnMainThread(context: Context) {
        if (ready || initFailed || initializing) return

        synchronized(initLock) {
            if (ready || initFailed || initializing) return
            initializing = true
            VoiceLog.i("TestPcm", "Initializing TextToSpeech for PCM synthesis…")
            tts = TextToSpeech(context) { status ->
                val engine = tts
                initializing = false
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    initFailed = true
                    VoiceLog.w("TestPcm", "TextToSpeech init failed (status=$status)")
                    failPending()
                    return@TextToSpeech
                }
                when (engine.setLanguage(Locale.US)) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED,
                    -> {
                        initFailed = true
                        VoiceLog.w("TestPcm", "English TTS unavailable on device")
                        failPending()
                    }
                    else -> {
                        ready = true
                        VoiceLog.i("TestPcm", "TextToSpeech ready for PCM synthesis")
                        flushPending(context)
                    }
                }
            }
        }
    }

    private fun flushPending(context: Context) {
        val queued = pending.toList()
        pending.clear()
        queued.forEach { (text, onComplete) -> generateNow(context, text, onComplete) }
    }

    private fun failPending() {
        val queued = pending.toList()
        pending.clear()
        queued.forEach { (_, onComplete) -> onComplete(null) }
    }

    private fun silencePcm(durationMs: Long): ByteArray =
        ByteArray((VoiceConstants.SAMPLE_RATE_HZ * durationMs / 1_000L * 2).toInt())

    private fun decodeWavToPcm24k(file: File): ByteArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "WAV too small" }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV file" }

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var audioOffset = 0
        var audioSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkDataOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    channels = ByteBuffer.wrap(bytes, chunkDataOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    sampleRate = ByteBuffer.wrap(bytes, chunkDataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = ByteBuffer.wrap(bytes, chunkDataOffset + 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                }
                "data" -> {
                    audioOffset = chunkDataOffset
                    audioSize = chunkSize
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize % 2)
        }

        require(sampleRate > 0 && channels > 0 && bitsPerSample == 16 && audioSize > 0) {
            "Unsupported WAV format (rate=$sampleRate channels=$channels bits=$bitsPerSample)"
        }

        val pcm = bytes.copyOfRange(audioOffset, audioOffset + audioSize)
        val mono = if (channels == 1) pcm else downmixToMono(pcm, channels)
        return if (sampleRate == VoiceConstants.SAMPLE_RATE_HZ) {
            mono
        } else {
            resamplePcm16(mono, sampleRate, VoiceConstants.SAMPLE_RATE_HZ)
        }
    }

    private fun downmixToMono(interleaved: ByteArray, channels: Int): ByteArray {
        val frameCount = interleaved.size / (2 * channels)
        val mono = ByteArray(frameCount * 2)
        val input = ByteBuffer.wrap(interleaved).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val output = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frameCount) {
            var sum = 0
            repeat(channels) { sum += input.get().toInt() }
            output.putShort((sum / channels).toShort())
        }
        return mono
    }

    private fun resamplePcm16(pcm: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        val inputSamples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val input = ShortArray(inputSamples.remaining())
        inputSamples.get(input)
        if (input.isEmpty()) return ByteArray(0)
        val outputCount = (input.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
        val output = ByteArray(outputCount * 2)
        val outBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        repeat(outputCount) { outIndex ->
            val srcPos = outIndex.toDouble() * fromRate / toRate
            val srcIndex = srcPos.toInt().coerceIn(0, input.lastIndex)
            val frac = srcPos - srcIndex
            val nextIndex = (srcIndex + 1).coerceAtMost(input.lastIndex)
            val sample = (input[srcIndex] + (input[nextIndex] - input[srcIndex]) * frac)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuffer.putShort(sample.toShort())
        }
        return output
    }
}