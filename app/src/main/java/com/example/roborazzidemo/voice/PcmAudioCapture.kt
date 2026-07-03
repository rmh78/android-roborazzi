package com.example.roborazzidemo.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class PcmAudioCapture {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun start(
        scope: CoroutineScope,
        warmupMs: Long = CAPTURE_WARMUP_MS,
        onChunk: (String) -> Unit,
    ) {
        stop()

        val frameSamples = FRAME_SIZE_BYTES / BYTES_PER_SAMPLE
        val warmupChunks = (warmupMs / FRAME_DURATION_MS).toInt().coerceAtLeast(0)
        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            error("Microphone does not support ${VoiceConstants.SAMPLE_RATE_HZ}Hz PCM capture")
        }
        val bufferSize = minBuffer.coerceAtLeast(FRAME_SIZE_BYTES * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord failed to initialize")
        }

        enableAudioEffects(record.audioSessionId)
        audioRecord = record
        record.startRecording()
        VoiceLog.i(
            "Mic",
            "Capture started (rate=${VoiceConstants.SAMPLE_RATE_HZ}Hz, warmup=${warmupMs}ms, " +
                "warmup_chunks=$warmupChunks, buffer=$bufferSize)",
        )

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(frameSamples)
            var chunksRead = 0
            var chunksSent = 0
            var warmupLogged = false
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> {
                        val level = calculateLevel(buffer, read)
                        _audioLevel.value = level
                        if (chunksRead >= warmupChunks) {
                            if (!warmupLogged) {
                                VoiceLog.i("Mic", "Warmup complete — streaming audio to server")
                                warmupLogged = true
                            }
                            val bytes = shortsToBytes(buffer, read)
                            onChunk(Base64.encodeToString(bytes, Base64.NO_WRAP))
                            chunksSent++
                            if (chunksSent == 1 || chunksSent % MIC_LEVEL_LOG_INTERVAL == 0) {
                                VoiceLog.d("Mic", "level=${"%.2f".format(level)} chunks_sent=$chunksSent")
                            }
                        }
                        chunksRead++
                    }
                    read < 0 -> error("AudioRecord.read failed with code $read")
                }
            }
        }
    }

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

    private fun enableAudioEffects(audioSessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioSessionId)?.enabled = true
        }
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioSessionId)?.enabled = true
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioSessionId)?.enabled = true
        }
    }

    private fun calculateLevel(buffer: ShortArray, size: Int): Float {
        if (size == 0) return 0f
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE
            sum += sample * sample
        }
        return (sqrt(sum / size) * 4.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_DURATION_MS = 20.0
        const val FRAME_SIZE_BYTES =
            (VoiceConstants.SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000 * BYTES_PER_SAMPLE).toInt()
        const val CAPTURE_WARMUP_MS = 1_200L
        private const val MIC_LEVEL_LOG_INTERVAL = 50
    }

    private fun shortsToBytes(buffer: ShortArray, size: Int): ByteArray {
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            val value = buffer[i].toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = (value shr 8 and 0xFF).toByte()
        }
        return bytes
    }
}