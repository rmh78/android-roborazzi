package com.example.roborazzidemo.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Mic capture aligned with xAI's
 * [VoiceAudioCapture](https://github.com/xai-org/xai-cookbook/tree/main/Android/VoiceApiAndroidExample):
 * PCM16 @ 24 kHz, 20 ms frames, [MediaRecorder.AudioSource.VOICE_COMMUNICATION],
 * platform noise/echo/gain effects, and mute (not stop/start).
 */
class PcmAudioCapture(
    private val context: Context,
) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioEffects: List<AudioEffect> = emptyList()

    @Volatile
    private var isCapturing = false

    @Volatile
    private var isMuted = false

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            _audioLevel.value = 0f
        }
    }

    fun isMuted(): Boolean = isMuted

    fun start(onChunk: (String) -> Unit, onFailure: (String) -> Unit = {}) {
        if (isCapturing) {
            setMuted(false)
            return
        }
        stop()
        isCapturing = true
        isMuted = false
        recordingThread = Thread(
            {
                val initError = openCapture()
                if (initError != null) {
                    isCapturing = false
                    _audioLevel.value = 0f
                    VoiceLog.e("Mic", initError)
                    onFailure(initError)
                    return@Thread
                }

                VoiceLog.i(
                    "Mic",
                    "Capture active on VOICE_COMMUNICATION " +
                        "(${SAMPLE_RATE_HZ}Hz PCM16, ${FRAME_DURATION_MS}ms frames)",
                )
                try {
                    captureLoop(onChunk)
                } catch (e: Exception) {
                    VoiceLog.e("Mic", "Capture loop failed", e)
                    onFailure(e.message ?: "Microphone capture failed")
                }
            },
            "voice-mic-capture",
        ).also { it.start() }
    }

    private fun openCapture(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Microphone permission not granted"
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return "Microphone does not support ${SAMPLE_RATE_HZ}Hz PCM16 capture"
        }

        val bufferSize = minBuffer.coerceAtLeast(FRAME_SIZE_BYTES * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return "AudioRecord failed to initialize"
        }

        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return "AudioRecord failed to enter RECORDSTATE_RECORDING"
        }

        audioRecord = record
        audioEffects = attachAndEnableAudioEffects(record.audioSessionId)
        VoiceLog.i(
            "Mic",
            "AudioRecord started (source=VOICE_COMMUNICATION, buffer=$bufferSize, " +
                "effects=${audioEffects.size})",
        )
        return null
    }

    private fun captureLoop(onChunk: (String) -> Unit) {
        val record = audioRecord ?: return
        val frameBuffer = ByteArray(FRAME_SIZE_BYTES)
        var chunksSent = 0

        while (isCapturing) {
            while (isCapturing && isMuted) {
                Thread.sleep(MUTE_POLL_MS)
            }
            if (!isCapturing) break

            val bytesRead = record.read(frameBuffer, 0, FRAME_SIZE_BYTES)
            when {
                bytesRead > 0 -> {
                    val chunk = if (bytesRead == frameBuffer.size) {
                        frameBuffer
                    } else {
                        frameBuffer.copyOf(bytesRead)
                    }
                    val rawRms = rawRmsFromPcm16(chunk)
                    _audioLevel.value = (rawRms * LEVEL_UI_GAIN).coerceIn(0f, 1f)
                    onChunk(Base64.encodeToString(chunk, Base64.NO_WRAP))
                    chunksSent++
                    if (chunksSent == 1 || chunksSent % MIC_LEVEL_LOG_INTERVAL == 0) {
                        VoiceLog.d(
                            "Mic",
                            "level=${"%.2f".format(_audioLevel.value)} " +
                                "rms=${"%.3f".format(rawRms)} chunks_sent=$chunksSent",
                        )
                    }
                }
                bytesRead < 0 -> {
                    VoiceLog.e("Mic", "AudioRecord.read failed with code $bytesRead")
                    isCapturing = false
                }
            }
        }
    }

    fun isCapturing(): Boolean = isCapturing

    fun stop() {
        isCapturing = false
        isMuted = false
        recordingThread?.join(500)
        recordingThread = null
        releaseRecord()
        _audioLevel.value = 0f
    }

    private fun releaseRecord() {
        audioEffects.forEach { effect ->
            try {
                effect.release()
            } catch (e: IllegalStateException) {
                VoiceLog.w("Mic", "AudioEffect.release skipped: ${e.message}")
            }
        }
        audioEffects = emptyList()

        val record = audioRecord ?: return
        audioRecord = null
        VoiceLog.i("Mic", "Capture stopped (source=VOICE_COMMUNICATION)")
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (e: IllegalStateException) {
            VoiceLog.w("Mic", "AudioRecord.stop skipped: ${e.message}")
        }
        try {
            record.release()
        } catch (e: IllegalStateException) {
            VoiceLog.w("Mic", "AudioRecord.release skipped: ${e.message}")
        }
    }

    private fun rawRmsFromPcm16(chunk: ByteArray): Float {
        if (chunk.size < 2) return 0f
        val shorts = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sum = 0.0
        val count = shorts.remaining()
        if (count == 0) return 0f
        while (shorts.hasRemaining()) {
            val sample = shorts.get() / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / count).toFloat()
    }

    private fun attachAndEnableAudioEffects(audioSessionId: Int): List<AudioEffect> = buildList {
        fun tryEnableEffect(factory: () -> AudioEffect?): AudioEffect? {
            val effect = try {
                factory()
            } catch (_: Exception) {
                null
            } ?: return null
            return try {
                effect.enabled = true
                effect
            } catch (_: Exception) {
                effect.release()
                null
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            tryEnableEffect { NoiseSuppressor.create(audioSessionId) }?.let(::add)
        }
        if (AcousticEchoCanceler.isAvailable()) {
            tryEnableEffect { AcousticEchoCanceler.create(audioSessionId) }?.let(::add)
        }
        if (AutomaticGainControl.isAvailable()) {
            tryEnableEffect { AutomaticGainControl.create(audioSessionId) }?.let(::add)
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = VoiceConstants.SAMPLE_RATE_HZ
        private const val FRAME_DURATION_MS = 20.0
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_SIZE_BYTES =
            (SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000 * BYTES_PER_SAMPLE).toInt()
        private const val MIC_LEVEL_LOG_INTERVAL = 50
        private const val LEVEL_UI_GAIN = 10f
        private const val MUTE_POLL_MS = 20L
    }
}