package com.example.roborazzidemo.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class PcmAudioCapture {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun start(
        scope: CoroutineScope,
        onChunk: (String) -> Unit,
    ) {
        stop()

        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) {
            error("Microphone does not support ${VoiceConstants.SAMPLE_RATE_HZ}Hz PCM_FLOAT capture")
        }

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
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
            error("AudioRecord failed to initialize")
        }

        audioRecord = record
        record.startRecording()
        VoiceLog.i(
            "Mic",
            "Capture started (rate=${VoiceConstants.SAMPLE_RATE_HZ}Hz, chunk=${CHUNK_DURATION_MS}ms, " +
                "encoding=PCM_FLOAT)",
        )

        captureJob = scope.launch(Dispatchers.IO) {
            val readBuffer = FloatArray(READ_BUFFER_SAMPLES)
            val pendingChunks = mutableListOf<FloatArray>()
            var pendingSamples = 0
            var chunksSent = 0

            while (isActive) {
                val read = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> {
                        val samples = readBuffer.copyOf(read)
                        _audioLevel.value = calculateLevel(samples)
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
                                    "level=${"%.2f".format(_audioLevel.value)} chunks_sent=$chunksSent",
                                )
                            }
                        }
                    }
                    read < 0 -> error("AudioRecord.read failed with code $read")
                }
            }
        }
    }

    fun isCapturing(): Boolean = captureJob?.isActive == true

    fun stop() {
        if (audioRecord != null) {
            VoiceLog.i("Mic", "Capture stopped")
        }
        captureJob?.cancel()
        captureJob = null
        audioRecord?.run {
            stop()
            release()
        }
        audioRecord = null
        _audioLevel.value = 0f
    }

    private fun calculateLevel(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            sum += (sample * sample).toDouble()
        }
        return sqrt(sum / samples.size).toFloat().coerceIn(0f, 1f)
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

    companion object {
        private const val CHUNK_DURATION_MS = 100
        private const val READ_BUFFER_SAMPLES = 4096
        private const val CHUNK_SIZE_SAMPLES =
            VoiceConstants.SAMPLE_RATE_HZ * CHUNK_DURATION_MS / 1000
        private const val MIC_LEVEL_LOG_INTERVAL = 10
    }
}