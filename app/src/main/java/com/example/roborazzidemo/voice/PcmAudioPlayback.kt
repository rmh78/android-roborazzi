package com.example.roborazzidemo.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList

class PcmAudioPlayback {
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedList<FloatArray>()
    private val lock = Any()
    private var isPlaying = false
    private var chunksPlayed = 0

    fun playBase64Chunk(base64Audio: String) {
        if (base64Audio.isEmpty()) return
        val samples = base64Pcm16ToFloat32(base64Audio)
        synchronized(lock) {
            playbackQueue.add(samples)
            if (!isPlaying) {
                isPlaying = true
                Thread { playNextChunk() }.start()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            playbackQueue.clear()
            isPlaying = false
        }
        audioTrack?.run {
            stop()
            release()
        }
        audioTrack = null
        if (chunksPlayed > 0) {
            VoiceLog.i("Playback", "Stopped (chunks_played=$chunksPlayed)")
        }
        chunksPlayed = 0
    }

    private fun playNextChunk() {
        while (true) {
            val chunk: FloatArray?
            synchronized(lock) {
                if (playbackQueue.isEmpty()) {
                    isPlaying = false
                    return
                }
                chunk = playbackQueue.poll()
            }

            val samples = chunk ?: continue
            ensureTrack()
            val track = audioTrack ?: return
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                VoiceLog.e("Playback", "AudioTrack.write failed with code $written")
                return
            }
            chunksPlayed++
            if (chunksPlayed == 1 || chunksPlayed % PLAYBACK_LOG_INTERVAL == 0) {
                VoiceLog.d(
                    "Playback",
                    "Playing assistant audio (chunks=$chunksPlayed, samples=${samples.size}, written=$written)",
                )
            }
        }
    }

    private fun ensureTrack() {
        if (audioTrack != null) return
        val minBuffer = AudioTrack.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VoiceConstants.SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { track ->
                track.setVolume(1f)
                track.play()
                VoiceLog.i(
                    "Playback",
                    "AudioTrack created (${VoiceConstants.SAMPLE_RATE_HZ}Hz mono PCM_FLOAT, voice communication)",
                )
            }
    }

    private fun base64Pcm16ToFloat32(base64: String): FloatArray {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shorts)
        return FloatArray(shorts.size) { index ->
            shorts[index] / 32768f
        }
    }

    companion object {
        private const val PLAYBACK_LOG_INTERVAL = 25
    }
}