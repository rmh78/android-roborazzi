package com.example.roborazzidemo.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class PcmAudioCapture(
    private val context: Context,
) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isCapturing = false

    @Volatile
    private var activeSource: Int? = null

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun start(onChunk: (String) -> Unit, onFailure: (String) -> Unit = {}) {
        stop()
        isCapturing = true
        recordingThread = Thread(
            {
                val sources = VoiceDeviceHints.preferredCaptureSources()
                var lastError: String? = null

                val emulator = VoiceDeviceHints.isLikelyEmulator()
                for (source in sources) {
                    if (!isCapturing) return@Thread
                    releaseRecord()

                    val initError = openCapture(source)
                    if (initError != null) {
                        lastError = initError
                        VoiceLog.w("Mic", "Capture source ${sourceName(source)} failed: $initError")
                        continue
                    }

                    if (emulator) {
                        VoiceLog.i(
                            "Mic",
                            "Emulator capture active on ${sourceName(source)} — " +
                                "streaming without silence probe (host mic may be quiet until you speak)",
                        )
                        captureLoop(source, onChunk, failOnSilence = false)
                        return@Thread
                    }

                    val peakLevel = captureLoop(source, onChunk, failOnSilence = true)
                    if (peakLevel > SILENCE_LEVEL_THRESHOLD) {
                        return@Thread
                    }

                    VoiceLog.w(
                        "Mic",
                        "No input on ${sourceName(source)} (peak=${"%.3f".format(peakLevel)}) — trying next source",
                    )
                    lastError = "No microphone input on ${sourceName(source)}"
                }

                isCapturing = false
                _audioLevel.value = 0f
                val message = lastError ?: "No microphone capture source available"
                VoiceLog.e("Mic", message)
                onFailure(
                    if (emulator) {
                        "$message. Emulator mic could not be opened. Try Extended Controls → " +
                            "Microphone → \"Virtual microphone uses host audio input\", or on debug builds " +
                            "inject speech with adb: adb shell am broadcast -a " +
                            "com.example.roborazzidemo.VOICE_SPOKEN --es text \"your question\" " +
                            "com.example.roborazzidemo"
                    } else {
                        message
                    },
                )
            },
            "voice-mic-capture",
        ).also { it.start() }
    }

    private fun openCapture(source: Int): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Microphone permission not granted"
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) {
            return "Microphone does not support ${VoiceConstants.SAMPLE_RATE_HZ}Hz PCM_FLOAT capture"
        }

        val record = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VoiceConstants.SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .build()

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
        activeSource = source
        VoiceLog.i(
            "Mic",
            "Capture started (rate=${VoiceConstants.SAMPLE_RATE_HZ}Hz, chunk=${CHUNK_DURATION_MS}ms, " +
                "source=${sourceName(source)}, encoding=PCM_FLOAT)",
        )
        return null
    }

    private fun captureLoop(
        source: Int,
        onChunk: (String) -> Unit,
        failOnSilence: Boolean,
    ): Float {
        val record = audioRecord ?: return 0f
        val readBuffer = FloatArray(READ_BUFFER_SAMPLES)
        val pendingChunks = mutableListOf<FloatArray>()
        var pendingSamples = 0
        var chunksSent = 0
        var peakLevel = 0f

        while (isCapturing) {
            val read = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
            when {
                read > 0 -> {
                    val samples = readBuffer.copyOf(read)
                    val level = calculateLevel(samples)
                    _audioLevel.value = level
                    peakLevel = maxOf(peakLevel, level)
                    pendingChunks.add(samples)
                    pendingSamples += read

                    while (pendingSamples >= CHUNK_SIZE_SAMPLES) {
                        val chunk = FloatArray(CHUNK_SIZE_SAMPLES)
                        var offset = 0
                        while (offset < CHUNK_SIZE_SAMPLES && pendingChunks.isNotEmpty()) {
                            val buffer = pendingChunks.first()
                            val needed = CHUNK_SIZE_SAMPLES - offset
                            if (buffer.size <= needed) {
                                System.arraycopy(buffer, 0, chunk, offset, buffer.size)
                                offset += buffer.size
                                pendingSamples -= buffer.size
                                pendingChunks.removeAt(0)
                            } else {
                                System.arraycopy(buffer, 0, chunk, offset, needed)
                                pendingChunks[0] = buffer.copyOfRange(needed, buffer.size)
                                offset += needed
                                pendingSamples -= needed
                            }
                        }
                        onChunk(float32ToPcm16Base64(chunk))
                        chunksSent++
                        if (chunksSent == 1 || chunksSent % MIC_LEVEL_LOG_INTERVAL == 0) {
                            VoiceLog.d(
                                "Mic",
                                "level=${"%.2f".format(level)} peak=${"%.2f".format(peakLevel)} " +
                                    "chunks_sent=$chunksSent source=${sourceName(source)}",
                            )
                        }
                        if (
                            failOnSilence &&
                            chunksSent >= SILENCE_RETRY_CHUNK_COUNT &&
                            peakLevel <= SILENCE_LEVEL_THRESHOLD
                        ) {
                            return peakLevel
                        }
                    }
                }
                read < 0 -> {
                    VoiceLog.e("Mic", "AudioRecord.read failed with code $read")
                    return peakLevel
                }
            }
        }
        return peakLevel
    }

    fun isCapturing(): Boolean = isCapturing

    fun stop() {
        isCapturing = false
        recordingThread?.join(500)
        recordingThread = null
        releaseRecord()
        _audioLevel.value = 0f
    }

    private fun releaseRecord() {
        val record = audioRecord ?: return
        val source = activeSource
        audioRecord = null
        activeSource = null
        VoiceLog.i("Mic", "Capture stopped (source=${source?.let(::sourceName)})")
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

    private fun calculateLevel(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            sum += (sample * sample).toDouble()
        }
        val rms = sqrt(sum / samples.size).toFloat()
        return (rms * LEVEL_UI_GAIN).coerceIn(0f, 1f)
    }

    private fun float32ToPcm16Base64(samples: FloatArray): String {
        val shorts = ShortArray(samples.size)
        for (i in samples.indices) {
            val sample = (samples[i] * 32767f).coerceIn(-32768f, 32767f)
            shorts[i] = sample.toInt().toShort()
        }
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        else -> source.toString()
    }

    companion object {
        private const val CHUNK_DURATION_MS = 100
        private const val READ_BUFFER_SAMPLES = 4096
        private const val CHUNK_SIZE_SAMPLES =
            VoiceConstants.SAMPLE_RATE_HZ * CHUNK_DURATION_MS / 1000
        private const val MIC_LEVEL_LOG_INTERVAL = 10
        private const val LEVEL_UI_GAIN = 10f
        private const val SILENCE_LEVEL_THRESHOLD = 0.01f
        private const val SILENCE_RETRY_CHUNK_COUNT = 30
    }
}