package com.example.roborazzidemo.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64

class PcmAudioPlayback {
    private var audioTrack: AudioTrack? = null

    fun playBase64Chunk(base64Audio: String) {
        if (base64Audio.isEmpty()) return
        val pcm = Base64.decode(base64Audio, Base64.DEFAULT)
        ensureTrack()
        audioTrack?.write(pcm, 0, pcm.size)
    }

    fun stop() {
        audioTrack?.run {
            stop()
            release()
        }
        audioTrack = null
    }

    private fun ensureTrack() {
        if (audioTrack != null) return
        val minBuffer = AudioTrack.getMinBufferSize(
            VoiceConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VoiceConstants.SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(VoiceConstants.SAMPLE_RATE_HZ))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }
}