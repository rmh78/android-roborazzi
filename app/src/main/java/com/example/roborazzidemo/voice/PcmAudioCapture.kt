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
import kotlin.math.sqrt

class PcmAudioCapture {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    fun start(scope: CoroutineScope, onChunk: (String) -> Unit) {
        stop()

        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = minBuffer.coerceAtLeast(VoiceConstants.SAMPLE_RATE_HZ / 10)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord failed to initialize")
        }

        audioRecord = record
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    _audioLevel.value = calculateLevel(buffer, read)
                    val bytes = shortsToBytes(buffer, read)
                    onChunk(Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.run {
            stop()
            release()
        }
        audioRecord = null
        _audioLevel.value = 0f
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